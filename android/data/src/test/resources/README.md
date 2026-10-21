`matches-v1.bin` is an ObjectOutputStream snapshot generated with the original
`domain` and `data` classes at `f6e6a91` (before typed dates/prices/photos).
It contains `sampleMatchData()` plus the original photoSlots entry `mia -> [-1, 2]`.
The migration test reads those original class descriptors, checks converted data,
then writes the new format and reopens it. No legacy classes are loaded at runtime.
