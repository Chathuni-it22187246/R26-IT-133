"""
RAG + web-retrieval pipeline for greenhouse infection treatment guides.
Retrieves from a curated knowledge base and trusted extension pages, then
synthesizes a structured professional response (optional OpenAI refinement).
"""

from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.parse import quote_plus

import requests
from bs4 import BeautifulSoup

BASE_DIR = Path(__file__).resolve().parent
KB_PATH = BASE_DIR / "knowledge_base.json"

USER_AGENT = (
    "GreenHandsBot/1.0 (+https://greenhands.local; agricultural decision support; research)"
)

# Trusted agricultural extension / pathology style sources (search landing pages).
TRUSTED_SEARCH_TEMPLATES = [
    "https://www.google.com/search?q=site%3Aextension.org+{q}+greenhouse+treatment",
    "https://duckduckgo.com/html/?q={q}+greenhouse+disease+management+extension",
]

DIRECT_EXTENSION_HINTS = [
    "https://extension.umn.edu/search?search={q}",
    "https://extension.psu.edu/search?q={q}",
]


@dataclass
class RetrievedChunk:
    source: str
    text: str
    score: float


def _load_knowledge_base() -> dict[str, Any]:
    with KB_PATH.open(encoding="utf-8") as f:
        return json.load(f)


def normalize_infection_name(text: str) -> str:
    cleaned = re.sub(r"\s+", " ", (text or "").strip())
    return cleaned.title() if cleaned else "Unknown Infection"


def match_kb_entry(infection: str, kb: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    needle = infection.lower().strip()
    for name, entry in kb.items():
        if name == "Default":
            continue
        aliases = [a.lower() for a in entry.get("aliases", [])] + [name.lower()]
        if any(a in needle or needle in a for a in aliases):
            return name, entry
    return "Default", kb["Default"]


def _tokenize(text: str) -> set[str]:
    return {t for t in re.findall(r"[a-z0-9]+", text.lower()) if len(t) > 2}


def score_chunk(query: str, text: str) -> float:
    q = _tokenize(query)
    t = _tokenize(text)
    if not q or not t:
        return 0.0
    overlap = len(q & t)
    return overlap / max(len(q), 1)


def retrieve_from_kb(infection: str, crop: str | None = None) -> list[RetrievedChunk]:
    kb = _load_knowledge_base()
    name, entry = match_kb_entry(infection, kb)
    sections = []
    for key in (
        "immediate_action",
        "biological_treatment",
        "chemical_control",
        "prevention",
        "environmental_adjustments",
    ):
        items = entry.get(key, [])
        sections.append(f"{key.replace('_', ' ').title()}: " + "; ".join(items))
    blob = f"{name}. " + " ".join(sections)
    query = f"{infection} {crop or ''}"
    return [
        RetrievedChunk(
            source=f"local_kb:{name}",
            text=blob,
            score=max(score_chunk(query, blob), 0.5),
        )
    ]


def scrape_extension_snippets(infection: str, crop: str | None = None, limit: int = 4) -> list[RetrievedChunk]:
    """Best-effort scrape of public search/result text from extension-oriented queries."""
    query = f"{infection} {crop or 'greenhouse'} treatment prevention IPM"
    chunks: list[RetrievedChunk] = []
    session = requests.Session()
    session.headers.update({"User-Agent": USER_AGENT, "Accept-Language": "en-US,en;q=0.9"})

    urls = [tpl.format(q=quote_plus(query)) for tpl in TRUSTED_SEARCH_TEMPLATES[:1]]
    urls += [tpl.format(q=quote_plus(infection)) for tpl in DIRECT_EXTENSION_HINTS[:1]]

    for url in urls:
        try:
            resp = session.get(url, timeout=8)
            if resp.status_code != 200:
                continue
            soup = BeautifulSoup(resp.text, "lxml")
            texts: list[str] = []
            for tag in soup.find_all(["p", "li", "a", "span"]):
                t = " ".join(tag.get_text(" ", strip=True).split())
                if 40 < len(t) < 320 and any(
                    k in t.lower()
                    for k in ("fung", "blight", "mildew", "disease", "spray", "manage", "control")
                ):
                    texts.append(t)
            for t in texts[:limit]:
                chunks.append(
                    RetrievedChunk(
                        source=url.split("?")[0],
                        text=t,
                        score=score_chunk(query, t),
                    )
                )
        except requests.RequestException:
            continue

    chunks.sort(key=lambda c: c.score, reverse=True)
    return chunks[:limit]


def retrieve_context(infection: str, crop: str | None = None) -> list[RetrievedChunk]:
    kb_chunks = retrieve_from_kb(infection, crop)
    web_chunks = scrape_extension_snippets(infection, crop)
    merged = kb_chunks + web_chunks
    merged.sort(key=lambda c: c.score, reverse=True)
    return merged[:6]


def _optional_llm_refine(guide: dict[str, Any], context: list[RetrievedChunk]) -> dict[str, Any]:
    """If OPENAI_API_KEY is set, refine wording via chat completions; otherwise return as-is."""
    api_key = os.getenv("OPENAI_API_KEY", "").strip()
    if not api_key:
        return guide

    try:
        ctx = "\n".join(f"- ({c.source}) {c.text}" for c in context[:4])
        prompt = (
            "You are an agricultural plant pathologist. Refine this greenhouse treatment JSON "
            "using the retrieved context. Keep the same keys. Return JSON only.\n\n"
            f"Context:\n{ctx}\n\nDraft JSON:\n{json.dumps(guide)}"
        )
        resp = requests.post(
            "https://api.openai.com/v1/chat/completions",
            headers={
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
            },
            json={
                "model": os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
                "temperature": 0.2,
                "messages": [
                    {"role": "system", "content": "Return only valid JSON."},
                    {"role": "user", "content": prompt},
                ],
            },
            timeout=30,
        )
        if resp.status_code != 200:
            return guide
        content = resp.json()["choices"][0]["message"]["content"]
        content = content.strip()
        if content.startswith("```"):
            content = re.sub(r"^```(?:json)?\s*", "", content)
            content = re.sub(r"\s*```$", "", content)
        refined = json.loads(content)
        # Preserve required keys
        for key in guide:
            if key not in refined:
                refined[key] = guide[key]
        return refined
    except Exception:
        return guide


def synthesize_treatment_guide(
    infection: str,
    crop: str | None = None,
    stage: str | None = None,
) -> dict[str, Any]:
    infection_name = normalize_infection_name(infection)
    kb = _load_knowledge_base()
    matched_name, entry = match_kb_entry(infection_name, kb)
    display_name = infection_name if matched_name == "Default" else matched_name

    context = retrieve_context(display_name, crop)
    web_bits = [c.text for c in context if not c.source.startswith("local_kb:")]

    severity = entry.get("severity_default", "Moderate")
    if "blight" in display_name.lower():
        severity = "Critical"
    elif "mildew" in display_name.lower():
        severity = "High"

    crop_note = f" for {crop}" if crop else ""
    stage_note = f" during the {stage} stage" if stage else ""

    immediate = list(entry.get("immediate_action", []))
    biological = list(entry.get("biological_treatment", []))
    chemical = list(entry.get("chemical_control", []))
    prevention = list(entry.get("prevention", []))
    environmental = list(entry.get("environmental_adjustments", []))

    if web_bits:
        # Fold highest-signal scraped guidance into biological/prevention as supplemental notes.
        biological = biological + [f"Extension-sourced note: {web_bits[0]}"]
        if len(web_bits) > 1:
            prevention = prevention + [f"Field guidance: {web_bits[1]}"]

    sources = list(entry.get("sources", []))
    for c in context:
        if c.source not in sources and not c.source.startswith("local_kb:"):
            sources.append(c.source)

    summary = (
        f"AI treatment plan{crop_note}{stage_note} for {display_name}: "
        f"prioritize isolation, climate correction, then layered biological and labeled chemical controls."
    )

    guide = {
        "title": f"{display_name}: AI Treatment Guide",
        "description": summary,
        "urgency": severity,
        "infection_name": display_name,
        "severity_level": severity,
        "immediate_action": " ".join(immediate[:2]) if immediate else summary,
        "biological_treatment": biological,
        "chemical_control": chemical,
        "prevention": prevention,
        "environmental_adjustments": environmental,
        "sources": sources[:6],
        "crop": crop or "",
        "stage": stage or "",
    }
    return _optional_llm_refine(guide, context)
