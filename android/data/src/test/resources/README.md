`matches-v1.bin` is an ObjectOutputStream snapshot generated with the original
`domain` and `data` classes at `f6e6a91` (before typed dates/prices/photos).
It contains `sampleMatchData()` plus the original photoSlots entry `mia -> [-1, 2]`.
The migration test reads those original class descriptors, checks converted data,
then writes the new format and reopens it. No legacy classes are loaded at runtime.

`auth18-wrapped-v1.bin` uses the unchanged domain/data classes from `4ab64f42`
(issue #18). It contains the original `StoredMatches` wrapper, a non-demo account
`auth18_owner` with the public fixture password `auth18-password-🐈` hashed by the
original PBKDF2 verifier with a fresh random salt, populated profile/listing fields,
non-default filters, favorites/history, and typed `Listing[]`/`Profile[]` arrays.
The two demo verifiers are preserved too.

`typed19-matches-v2.bin` is a top-level typed `MatchData`, without credentials, using
the unchanged model shapes from `abd485b` (issue #19). Its extra profile/listing
exercise surname, separate WhatsApp, coordinates, verification and opaque photos.
It intentionally receives the documented demo password when auth is added.

`../fixtures/GenerateCompatibilityFixtures.java` generates these development-only
fixtures with the original compiled domain/data directories plus Kotlin stdlib on
its classpath: run with `auth18 <destination>` for the original #18 classes or
`typed19 <destination>` for the typed #19 classes. Production and tests never
compile or dynamically load those original classes. The source generator uses
only JDK reflection and does not participate in the Gradle test source sets.
The same #18 binary is packaged only in instrumentation-test assets to check the
real Android ObjectInputStream, login UI, profile updates and combined restart.

`typed19-wrapped-arrays-v2.bin` was generated with the unchanged domain/data sources
from `09effbf3a7732b1d6bb859c1d90f3e96f2b7832f`, compiled in a temporary directory.
Run the generator with `typed19-arrays <destination>` against those original classes.
It wraps the same typed data in `StoredMatches`, preserves original PBKDF2 credentials
(password `typed19-password-🐈` for `typed19_owner`), and stores photos in an authentic
`ListingPhoto[]` backing array. It covers migration of typed wrappers and arrays after
removing Serializable from domain. No historical classes enter production or test classpaths.

The JVM compatibility suite also reads a copy with the date proxy descriptor renamed
from `java.time.Ser` to `j$.time.Ser` (the same LocalDate external payload), ensuring
migration never requires platform/desugared date classes on the receiving runtime.
Both original auth18 and typed photo-array wrappers are exercised through Android UI,
profile saving, and credential-preserving restart.
