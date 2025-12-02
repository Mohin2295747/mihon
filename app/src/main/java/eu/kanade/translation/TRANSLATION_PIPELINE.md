# Translation Pipeline Architecture

## Overview

This document describes the complete translation pipeline in TachiyomiAT - how manga images are OCR'd, translated, and rendered with translated text overlays.

---

## Complete Pipeline Flowchart

```
┌─────────────────────────────────────────────────────────────────────┐
│                    1. TRIGGER TRANSLATION                           │
│   User taps "Translate" in reader menu                              │
│   OR auto-translate fires after chapter download                    │
│                                                                     │
│   Entry: TranslationManager.translateChapter(manga, chapter)        │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    2. QUEUE & LOAD IMAGES                           │
│   ChapterTranslator queues the job                                  │
│   Load chapter pages from disk/archive (downloaded images)          │
│   Status: QUEUE → RECOGNIZING                                       │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    3. OCR (TEXT RECOGNITION)                        │
│   For each page image:                                              │
│   ┌───────────────────────────────────────────────────────────────┐ │
│   │  MLKit TextRecognizer.recognize(image)                        │ │
│   │  • Uses language-specific model (Chinese/Japanese/Korean/Latin)│ │
│   │  • Returns Text.TextBlock objects with:                       │ │
│   │    - Bounding box (x, y, width, height)                       │ │
│   │    - Recognized text string                                    │ │
│   │    - Confidence score                                          │ │
│   │    - Detected language                                         │ │
│   └───────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                 4. LANGUAGE DETECTION & BLOCK MERGING               │
│   • Analyze recognized language from OCR results                    │
│   • Detect: Korean (ko), Japanese (ja), Chinese (zh), etc.          │
│   • smartMergeBlocks() - merge adjacent/overlapping text regions    │
│     with language-specific thresholds:                              │
│       Korean:   widthThresh=60, xThresh=35, yThresh=40              │
│       Japanese: widthThresh=50, xThresh=30, yThresh=30              │
│       Chinese:  widthThresh=45, xThresh=25, yThresh=28              │
│   • Create PageTranslation with TranslationBlock list               │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    5. TRANSLATION ENGINE                            │
│   Status: TRANSLATING                                               │
│   TextTranslator.translate(pages) - mutates block.translation       │
│                                                                     │
│   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐│
│   │   MLKit     │  │   Cloud     │  │   Gemini    │  │ OpenRouter ││
│   │ (On-device) │  │ Translation │  │    AI       │  │  (Multi-AI)││
│   │ No API key  │  │ Google API  │  │ 2.5 Flash   │  │ GPT/Claude ││
│   │ Offline     │  │ BYOK        │  │ Context-aware│ │   BYOK    ││
│   └─────────────┘  └─────────────┘  └─────────────┘  └────────────┘│
│                                                                     │
│   AI engines (Gemini/OpenRouter):                                   │
│   • Manga-aware system prompt                                       │
│   • Watermark detection → replaced with "RTMTH"                     │
│   • Batch translation via JSON structure                            │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    6. SAVE TO DISK                                  │
│   Status: SAVED                                                     │
│   Json.encodeToStream(pages, translationFile)                       │
│                                                                     │
│   Storage: translations/{source}/{manga}/{chapter}.json             │
│   Example: translations/MangaDex/One Piece/Chapter 1.json           │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│              7. DISPLAY IN READER (On Chapter Open)                 │
│   TranslationManager.getChapterTranslation() → Load JSON            │
│   Create PagerTranslationsView overlay on manga page                │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                 8. RENDER TRANSLATION BUBBLES                       │
│   For each TranslationBlock:                                        │
│   ┌───────────────────────────────────────────────────────────────┐ │
│   │  SmartTranslationBlock composable                             │ │
│   │  • Position at original (x, y) from OCR                       │ │
│   │  • Draw white background bubble                               │ │
│   │  • Render translated text on top                              │ │
│   │  • Apply LanguageRenderModule rules (CJK vs Latin)            │ │
│   └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
│   CJK Rendering (Korean/Japanese/Chinese):                          │
│   • Fixed 12dp font size                                            │
│   • Aggressive bubble expansion (1.8x-2.5x)                         │
│   • Conservative margins (70%)                                      │
│                                                                     │
│   Latin Rendering (English/Spanish/Indonesian):                     │
│   • Hybrid: expand bubble first, then reduce font (12→10dp)         │
│   • Limited expansion (1.0x-1.3x)                                   │
│   • Generous margins (95%)                                          │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Simplified Visual Summary

```
   Downloaded        OCR              Block           Translation       Rendered
   Manga Image  →  Detection  →    Extraction   →      Engine     →    Overlay

   ┌─────────┐    ┌─────────┐     ┌──────────┐     ┌──────────┐     ┌─────────┐
   │  JPG/   │    │ MLKit   │     │ Bounding │     │ MLKit/   │     │ White   │
   │  PNG    │ →  │ Text    │  →  │ Boxes +  │  →  │ Cloud/   │  →  │ bubble  │
   │  Image  │    │ Recog.  │     │ Text     │     │ Gemini/  │     │ with    │
   │         │    │         │     │          │     │ OpenRouter│    │ text    │
   └─────────┘    └─────────┘     └──────────┘     └──────────┘     └─────────┘
```

---

## Key Components

| Stage | File | Purpose |
|-------|------|---------|
| Entry Point | `TranslationManager.kt` | Public API facade |
| Orchestrator | `ChapterTranslator.kt` | Core processing logic |
| OCR | `recognizer/TextRecognizer.kt` | MLKit text recognition |
| Translation | `translator/*.kt` | MLKit, Cloud, Gemini, OpenRouter engines |
| Data Models | `model/PageTranslation.kt`, `TranslationBlock.kt` | Translation data structures |
| Rendering | `presentation/SmartTranslationBlock.kt` | Bubble rendering composable |
| Language Rules | `presentation/LanguageRenderModule.kt` | CJK vs Latin rendering configs |

---

## Key Insights

1. **OCR-based extraction** - MLKit Text Recognition extracts text with bounding boxes from manga images
2. **Text-only translation** - Only recognized strings are translated, not the image itself
3. **Overlay rendering** - Translated text drawn on TOP of original image at detected coordinates
4. **Language-specific optimization** - Different rendering rules for CJK (dense) vs Latin (wider) characters
5. **Persistent caching** - Translations saved to JSON files for instant re-display

---

## Translation State Machine

```
QUEUE → RECOGNIZING → TRANSLATING → SAVED
                 ↘         ↓
                   → → ERROR
```

| State | Description |
|-------|-------------|
| `QUEUE` | Waiting to start |
| `RECOGNIZING` | OCR in progress |
| `TRANSLATING` | Translation engine running |
| `SAVED` | Completed and persisted |
| `ERROR` | Failed (partial translations preserved) |
