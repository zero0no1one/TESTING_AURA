# Proposal alignment — AURA 1.1

Sources reviewed: `PROJECT PROPOSAL_ PROJECT AURA (1).pdf`, `Black-JEK_ConceptNote_PSC11.pdf`, and `Black-JEK_PresentationDeck_PSC11.pdf`. These describe the intended product and research objectives. The user's requirements govern implementation: Android APK, free tools/services, no paid APIs/trials, local audio, and optional Supabase Free metadata storage.

The documents identify the purpose as improving speech understanding in noisy environments, especially consultations, caregiving, and everyday interactions for people with speech-in-noise difficulty. The core message is **Understand the voice that matters.**

| Document requirement | Implemented behavior | Remaining gap |
| --- | --- | --- |
| Before–During–After (concept note, Summary; deck, In Action) | Visible journey; choose context/profile before listening, adjust real audio during it, optional reflection immediately afterward. | Feedback uses explicit rules; it does not train an AI model. |
| Contextual listening/Care Mode (concept note, Proposed Startup Solution) | Everyday, consultation, pharmacy, caregiving, family/friends, classroom/work, public transport; Care/Focus/Commute presets and adjustable controls. Context selection preserves current volume. | Context is chosen by the user, not inferred from environmental sound. |
| Personal Acoustic Profile | Saved named preferences include context, mode, clarity, reduction, surroundings, and volume. New sessions preserve initial settings/context/profile label. | No audiogram, selected-voice identity, or learned model parameters. |
| After-session personalization | Optional Clear/Okay/Difficult feedback; visible setting recommendation based on that session. User chooses whether to apply it; current volume never increases. | No automatic/trained acoustic adaptation. Older sessions without snapshots use current settings and are identified as such. |
| Privacy/local-first (concept note, Privacy-by-Design) | Microphone → local DSP → headphones; no audio files/upload. Cloud backup is explicit and limited to metadata. | Cloud metadata is not end-to-end encrypted and can be read by the project administrator. |
| Voice Focus: target-speaker enrollment/isolation | Product capability status explicitly identifies this as unavailable. Existing speech-band filtering remains available. | No speaker enrollment, identity embedding, or separation model. This flagship proposal objective is not complete. |
| Selective Awareness: at least five independently processed sound categories | Current processing is speech-oriented filtering, an adaptive expander, available Android noise suppression, and a surroundings mix. | No semantic classifier or source-by-source reconstruction. |
| Safe Awareness: preserve alarms, sirens, horns | Surroundings mix is adjustable; limitations are shown. | No event detector, protected threshold, or guarantee that warnings remain audible. |
| ≤100 ms latency and improved intelligibility | Processing-block timing is displayed accurately as processing time. | End-to-end latency and speech-in-noise benefit need physical device/headphone measurements and human evaluation. |
| Healthcare validation | Context and guidance support experimentation. | No clinical validation, certification, pilot outcome, or medical claim. |
| AURA SDK, institutional device management, pricing | Not part of the native prototype. | Future product/business work. Document prices do not create paywalls in this free app. |

## Visual references

The supplied waveform emblem was used for the navy/periwinkle palette, dotted outer ring, open middle ring, white inner circle, and five-bar center. The interface uses large touch targets, readable mobile layouts, five navigation destinations, and the proposal's terminology.

The linked Google Drive folder could not be opened: the environment proxy rejected it with HTTP 403. No demo-screen files from that folder were available. Consequently exact layout fidelity to those unseen screens is **unverified**. The logo is a visual reference, not a complete screen specification. Directly attached demo screens are needed for precise screen-by-screen matching.

## What this release means

This is a functional workflow/database upgrade to the existing local DSP prototype. It does not claim to complete the proposal's central AI research objectives. Fulfilling those objectives requires licensed model selection/training, mobile integration, adversarial acoustic evaluation, and controlled listening/latency tests. Calling filtering “Voice Focus” or a surroundings mix “Safe Awareness” would misrepresent the implementation.
