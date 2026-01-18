# Agent Context

This directory contains context files for AI coding assistants (Codex, Copilot, Claude).

## Files

- `architecture.md` - High-level system architecture
- `folder_structure.md` - Project directory structure
- `api_contracts.md` - API endpoints and schemas
- `business_logic.md` - Core business rules and domain logic
- `constraints_and_workarounds.md` - Known limitations and workarounds
- `decisions_log.md` - Chronological log of architectural decisions
- `database.md` - Database queries and stored procedures
- `update_instructions.md` - Instructions for updating context

## Generated Files

The `generated/` directory contains machine-generated context:
- `summary.json` - Compressed summaries of all context files
- `embeddings.sqlite` - Metadata and content for vector search
- `embeddings_index.faiss` - FAISS index for similarity search

Run `context-builder build` to regenerate these files.
