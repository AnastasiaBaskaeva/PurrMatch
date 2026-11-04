# PurrMatch API v1 contract

The data module currently runs an in-process mock backend. It uses the JSON request and response DTOs described here; it does not open an HTTP server or make network requests. The paths below define the intended `/v1` HTTP API. Mock method routing and failure rules use the paths without the `/v1` prefix.

Repositories convert [transport DTOs](../data/src/main/kotlin/com/purrmatch/data/backend/dto/BackendDtos.kt) with [explicit domain mappers](../data/src/main/kotlin/com/purrmatch/data/backend/dto/DtoMappers.kt). Presentation receives domain models and repository results, never transport DTOs. Kotlin serialization belongs to the data module. All fixture files are UTF-8 JVM classpath resources under `data/src/main/resources/mock`.

## Representation

JSON keys use camelCase. Identifiers are opaque strings. Prices are nonnegative whole Russian rubles stored as signed 64-bit integers; `0` means free. Birthdays are nullable ISO dates (`yyyy-MM-dd`), and ages are calculated from the birthday rather than stored. An unknown birthday is `null`. Photos are ordered opaque string references. A mock reference such as `mock://listing/mia/1.jpg` is resolved by the local photo adapter, not fetched from a server.

Coordinates are either `null` or `{ "latitude": 55.7558, "longitude": 37.6173 }`. Latitude must be finite and between -90 and 90; longitude must be finite and between -180 and 180. Coordinates remain part of profiles and listings for future map and distance features. Verification and cattery fields are intentionally absent from this contract.

`ProfileDto` contains `id`, `name`, `login`, `city`, `about`, `telegram`, `phone`, `website`, `surname`, `coordinates`, and `whatsapp`. Text fields default to an empty string and coordinates default to `null`.

`ListingDto` contains required `id` and `name`; `authorId`, `breed`, `color`, `eyes`, `character`, `description`, and `city` default to empty strings. `sex` is the Russian domain value `кошка` or `кот` (default `кошка`). `birthday` and `coordinates` default to `null`; `price` defaults to `0`; `photos` defaults to an empty list; `available`, `dogs`, `cats`, and `children` default to `true`. The three compatibility flags describe whether the cat can live with dogs, cats, and children.

`SelectionDto` contains `favorites` and `seen` identifier lists plus a `filters` object. Both lists default to empty. Filters have no UI placeholder text in JSON:

| Field | JSON type | Meaning |
| --- | --- | --- |
| `breed`, `color`, `eyes`, `city` | string or null | Exact selected value; null means unrestricted |
| `sex` | string or null | `кошка`, `кот`, or unrestricted |
| `minAge`, `maxAge` | integer or null | Inclusive age bounds in completed years |
| `maxPrice` | 64-bit integer or null | Inclusive maximum ruble price |
| `freeOnly` | boolean or null | true: free; false: paid; null: either |

The mapper translates unrestricted values to the existing domain/UI placeholders. Only the explicit placeholders mean unrestricted; an empty string remains a literal string filter. Domain sex `0/1/2` maps to `null/кошка/кот`, and domain terms `0/1/2` maps to `null/true/false`. A listing with unknown age does not satisfy a specified age bound. Price, age, and compatibility flags are independent.

## Operations

Each mock request waits 350 ms by default using a cancellable coroutine delay; tests and debug catalogs can set `latencyMillis=0`. The backend loads its selected JSON fixture and failure rules once on an IO dispatcher. Cancelling a delayed request propagates cancellation and does not apply its mutation.

The mock returns `BackendResponse<T>(status, body, error)`. A successful response has its typed body and no error; a failure has an `ErrorDto` and no body. For a future HTTP transport, successful bodies are JSON values and failed bodies are `{ "code": "…", "message": "…" }`. Empty successful mutations use an empty response body.

| Method and path | Request | Success body | Behavior |
| --- | --- | --- | --- |
| `POST /auth/register` | `{ "login", "password", "name" }` | `{ "userId" }` | Creates an account and an initially incomplete profile |
| `POST /auth/login` | `{ "login", "password" }` | `{ "userId" }` | Checks credentials |
| `GET /profiles/{id}` | — | `ProfileDto` | Reads an author or own profile |
| `PATCH /profiles/{id}` | `ProfileDto` | `ProfileDto` | Upserts the full profile representation; path id is authoritative |
| `GET /listings` | Query filters, optional `userId` and `authorId` | `ListingDto[]` | Available listings matching filters; excludes that user's seen listings |
| `GET /users/{id}/selection` | — | `SelectionDto` | The user's favorites, seen listings and saved filters |
| `GET /users/{id}/favorites` | — | `ListingDto[]` | Favorites in the order added, including unavailable listings |
| `GET /listings/browse` | Query `userId`, `limit` (1–50), optional `after` | `ListingPageDto` | Next portion of the user's feed: available, unseen listings of other authors matching the user's saved filters, in publication order |
| `GET /listings/{id}` | — | `ListingDto` | Includes unavailable listings so saved favorites remain viewable |
| `POST /listings?userId={id}` | `ListingDto` | `ListingDto` | Publishes a listing; author is forced to `userId` |
| `DELETE /listings/{id}?userId={id}` | — | — | Owner-only soft deletion: sets `available=false` |
| `POST /listings/{id}/favorite` | `{ "userId" }` | `SelectionDto` | Toggles the listing in favorites, including unavailable listings |
| `POST /listings/{id}/swipe` | `{ "userId", "like" }` | `SelectionDto` | Marks an available listing as seen; a like also adds a favorite |
| `PUT /users/{id}/filters` | `FiltersDto` | `SelectionDto` | Saves mock preferences used by subsequent browse requests |
| `POST /auth/session` | `{ "userId" }` | `{ "userId" }` | Sets the local mock session |
| `DELETE /auth/session` | — | `{ "userId": null }` | Clears the local mock session |

Successful registration and listing publication return `201`; deletion returns `204`; all other successful operations return `200`. The mock represents a successful delete body as Kotlin `Unit`; its future HTTP response has no body.

Logins are case-sensitive and surrounding whitespace is trimmed. Registration requires a nonblank login and name, and at least eight password characters. A used login is rejected. Registration returns an identifier; session creation is a separate operation. Profile upsert requires nonblank id, login, and name and valid coordinates. An existing profile's login is immutable, and a different profile cannot reuse its login. Upserting a profile alone does not create credentials.

Publishing requires a nonblank id and name, nonnegative price, valid coordinates, and `кошка` or `кот` for sex. A birthday must be a valid canonical `yyyy-MM-dd` date and cannot be in the future. Existing listing identifiers are rejected. Publication forces the caller's author id and sets `available=true`.

`GET /listings` uses the same field names as `FiltersDto` for query parameters, plus `userId`. Omitted filters are unrestricted; the caller explicitly supplies saved preferences when desired. Query numbers and booleans are their plain JSON scalar spellings. Breed, sex, and city compare exactly; color and eyes compare without case sensitivity. Ages and price bounds must be nonnegative, and minimum age cannot exceed maximum age. The filter preferences endpoint is a local convenience around browse state, rather than an authentication mechanism.

`GET /listings/browse` returns `{ "listings": ListingDto[], "nextCursor": string|null, "endReached": boolean }`. `after` is the `nextCursor` of the previous portion (the id of its last listing); omit it for the first portion. The cursor is a bookmark, not an offset: new listings are appended after it, so it never shifts when listings are published or swiped. `endReached` is true when fewer than `limit` listings were found right now; the cursor stays valid, and a later request with it returns listings published in the meantime. An empty portion returns the `after` it was given. An unknown cursor or a `limit` outside 1–50 is `422 VALIDATION_ERROR`. The app holds only a short queue of cards and asks for the next portion while a few cards remain.

A repeated swipe does not duplicate seen identifiers or favorites. A left swipe leaves existing favorites intact. Soft deletion preserves the listing identifier and favorite references. Publishing never trusts the request body's `authorId`.

The app never downloads the whole catalogue. Each screen asks for what it shows: `GET /users/{id}/selection` returns the user's `SelectionDto` (favorites, seen, filters); `GET /users/{id}/favorites` returns the user's favorite `ListingDto[]` in the order they were added, including unavailable ones; `GET /listings?authorId={id}` returns an author's available listings. `GET /profiles` and `GET /users/selections` remain only as mock-server test helpers and are not public v1 endpoints.

| Repository member | Mock request or behavior |
| --- | --- |
| `MatchRepository.data` | Observable cache of what was loaded so far (own profile and selection, plus entities of opened screens); does not make a request when read. Responses and successful mutations update it |
| `load(userId)` | `GET /profiles/{id}`, `GET /users/{id}/selection`; run once after sign-in, and again on retry |
| `getFavorites(userId)` | `GET /users/{id}/favorites` |
| `getAuthorListings(authorId)` | `GET /listings?authorId={id}` |
| `remember(listing)` | No request: keeps a card the user is about to open |
| `register(login, password, name)` | `POST /auth/register` |
| `authenticate(login, password)` | `POST /auth/login` |
| `getProfile(id)` | `GET /profiles/{id}` |
| `upsertProfile(profile)` | `PATCH /profiles/{profile.id}` |
| `getListings(userId, filters)` | `GET /listings` with explicit user id and typed filters |
| `browse(userId, limit, after)` | `GET /listings/browse`; read-only, so the cache is not rebuilt |
| `getListing(id)` | `GET /listings/{id}` |
| `publish(userId, listing)` | `POST /listings?userId={id}` |
| `delete(userId, listingId)` | `DELETE /listings/{id}?userId={id}` |
| `toggleFavorite(userId, listingId)` | `POST /listings/{id}/favorite` |
| `swipe(userId, listingId, like)` | `POST /listings/{id}/swipe` |
| `applyFilters(userId, filters)` | `PUT /users/{id}/filters` |
| `SessionRepository.session` | Observable local session state, initially `Guest` |
| `signIn(userId, onboarding)` | `POST /auth/session`; onboarding is local UI state |
| `signOut()` | `DELETE /auth/session` |

Every successful match repository operation synchronizes the observable domain cache with the shared in-memory backend. Rejected mutations leave it unchanged.

## Errors and deterministic failures

The backend supplies a status plus a stable error code and a user-readable message. The repository maps `INVALID_CREDENTIALS` to `InvalidCredentialsException` and `LOGIN_OCCUPIED` to `LoginOccupiedException`. Other errors become `RequestFailedException`, preserving status, code, and message; screens can retry operations without changing navigation.

| Status | Code | Trigger |
| --- | --- | --- |
| `401` | `INVALID_CREDENTIALS` | Unknown login or wrong password |
| `403` | `NOT_OWNER` | Deleting another author's listing |
| `404` | `PROFILE_NOT_FOUND` | Unknown profile or simulated user id |
| `404` | `LISTING_NOT_FOUND` | Unknown listing id |
| `409` | `LOGIN_OCCUPIED` | Registering or upserting a duplicate login |
| `409` | `LISTING_EXISTS` | Publishing an existing listing id |
| `409` | `LISTING_UNAVAILABLE` | Swiping an unavailable listing |
| `422` | `VALIDATION_ERROR` | Invalid profile, listing, registration, coordinates, or filter fields |
| `500` | `INTERNAL_ERROR` | Bundled deterministic failure rule |

[errors.json](../data/src/main/resources/mock/errors.json) is separate from catalog data. It contains `rules`, each with `method`, `path`, `status`, `error: { code, message }`, and an optional exact `login` match. The bundled rules make login and registration with login `server_error` return status `500` and code `INTERNAL_ERROR`. This gives error screens a repeatable scenario without a real network or random failures. Error rules do not add an account to fixtures.

## Standalone request and response examples

These files are actual transport bodies without fixture credentials or catalog wrappers: [login request](../data/src/main/resources/mock/examples/auth-login-request.json), [authentication response](../data/src/main/resources/mock/examples/auth-response.json), [profile response](../data/src/main/resources/mock/examples/profile-response.json), [listing response](../data/src/main/resources/mock/examples/listing-response.json), [listing collection response](../data/src/main/resources/mock/examples/listings-response.json), and [error response](../data/src/main/resources/mock/examples/error-response.json). Contract tests decode each file as its corresponding DTO.

## Fixtures and lifecycle

Each catalog scenario is a complete standalone `MockFixtureDto`: `profiles`, `listings`, `selections`, and `credentials`. Credentials contain `userId`, `login`, and `password` and are mock-only input, never a public response field.

| Fixture | Purpose |
| --- | --- |
| [default.json](../data/src/main/resources/mock/default.json) | 28 listings, eight authors, eight cities, varied breeds, ages, prices, compatibility flags, coordinates, and ordered photo references |
| [catalog.json](../data/src/main/resources/mock/catalog.json) | Original three-listing debug catalog with Anna's short settings biography; excludes Barsik |
| [no-results.json](../data/src/main/resources/mock/no-results.json) | Empty listing collection |
| [empty-favorites.json](../data/src/main/resources/mock/empty-favorites.json) | Normal debug listings and no Anna favorites |
| [complete-profile.json](../data/src/main/resources/mock/complete-profile.json) | Anna has empty city, biography and contact fields, plus no coordinates |
| [errors.json](../data/src/main/resources/mock/errors.json) | Deterministic operation failure rules |

Anna (`anna_cats`) and Olga (`olga_cats`) keep their original identifiers. The default fixture keeps listings `mia`, `peach`, `sonya`, and `barsik`; Sonya is unavailable. Anna initially favorites `mia`, `peach`, and `sonya`. All bundled demo authors use password `purrmatch-demo`. The loader accepts only the listed catalog fixture names and does not accept arbitrary paths.

All mutations, newly registered accounts, saved preferences, favorites, and session state live only in memory. They are lost when the process or backend is recreated. There is no disk snapshot, SharedPreferences account store, or migration from legacy serialized snapshots. Fixture resources are never rewritten.

The explicit `userId` parameters simulate identity; they are not real authorization. Fixture passwords are compared directly only inside this local mock. A production server must own account credentials and authorization and replace explicit simulated identity with a server-issued token. The client-facing repository interfaces allow that transport to replace the mock without exposing DTOs to presentation.
