import java.io.*;
import java.lang.reflect.*;
import java.time.LocalDate;
import java.util.*;

/** Run against the original compiled classes, never as part of production or tests. */
class GenerateCompatibilityFixtures {
    private static final String DOMAIN = "com.purrmatch.domain.match.";
    private static Class<?> type(String name) throws Exception { return Class.forName(DOMAIN + name); }
    private static Object make(String name, Object... values) throws Exception {
        for (Constructor<?> ctor : type(name).getConstructors()) {
            if (!ctor.isSynthetic() && ctor.getParameterCount() == values.length) return ctor.newInstance(values);
        }
        throw new IllegalArgumentException(name);
    }
    private static Object get(Object object, String name) throws Exception {
        return object.getClass().getMethod("get" + name).invoke(object);
    }
    @SuppressWarnings("unchecked")
    private static List<Object> list(Object object, String name) throws Exception {
        return new ArrayList<>((List<Object>) get(object, name));
    }
    private static List<?> typedList(String name, List<Object> values) throws Exception {
        Object[] array = (Object[]) Array.newInstance(type(name), values.size());
        return Arrays.asList(values.toArray(array));
    }
    private static void write(File file, Object value) throws Exception {
        try (ObjectOutputStream stream = new ObjectOutputStream(new FileOutputStream(file))) {
            stream.writeObject(value);
        }
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) throws Exception {
        boolean auth = args[0].equals("auth18");
        File destination = new File(args[1]);
        Object sample = Class.forName("com.purrmatch.data.match.MockMatchRepositoryKt")
            .getMethod("sampleMatchData").invoke(null);
        String id = auth ? "auth18-owner" : "typed19-owner";
        String login = auth ? "auth18_owner" : "typed19_owner";
        Object profile = auth ? make("Profile", id, "Светлана", login, "Тула", "Описание автора",
            "@legacy_owner", "+79991234567", "owner.example.com") :
            make("Profile", id, "Светлана", login, "Тула", "Описание автора", "@legacy_owner",
                "+79991234567", "owner.example.com", "Иванова", make("Coordinates", 54.193, 37.617),
                "+79997654321", Enum.valueOf((Class) type("VerificationStatus"), "APPROVED"), "Питомник");
        Object listing = auth ? make("Listing", "compat-cat", "Луна", id, "Метис", "кот", "2 года",
            "29.02.2024", "Чёрный", "Зелёные", "Игривый", "Полное описание", "Тула", "12 345 ₽",
            true, false, true, false) :
            make("Listing", "compat-cat", "Луна", id, "Метис", "кот", LocalDate.of(2024, 2, 29),
                "Чёрный", "Зелёные", "Игривый", "Полное описание", "Тула", 12_345L,
                Arrays.asList(make("ListingPhoto", "local:typed-cover"), make("ListingPhoto", "sample:upload")),
                make("Coordinates", 56.0, 37.0), true, false, true, false);
        List<Object> profiles = list(sample, "Profiles"); profiles.add(profile);
        List<Object> listings = list(sample, "Listings"); listings.add(listing);
        Object filters = make("SearchFilters", "Метис", 2, "Чёрный", "Зелёные", "1", "7", "Тула", 2, "12345");
        Object selection = make("UserSelection", Arrays.asList("mia", "compat-cat"), Arrays.asList("peach", "sonya"), filters);
        Map<String,Object> selections = new LinkedHashMap<>((Map<String,Object>) get(sample, "Selections"));
        selections.put(id, selection);
        Object data = auth ? make("MatchData", typedList("Profile", profiles), typedList("Listing", listings), selections,
            new LinkedHashMap<>(Map.of("mia", Arrays.asList(-1, 2), "compat-cat", Arrays.asList(-1, 1, 3)))) :
            make("MatchData", typedList("Profile", profiles), typedList("Listing", listings), selections);
        if (!auth) { write(destination, data); return; }
        Class<?> verifier = Class.forName("com.purrmatch.data.match.PasswordVerifier");
        Object companion = verifier.getField("Companion").get(null);
        Object demo = companion.getClass().getMethod("getLegacyFixture").invoke(companion);
        Object password = companion.getClass().getMethod("create", String.class).invoke(companion, "auth18-password-🐈");
        Map<String,Object> credentials = new LinkedHashMap<>();
        credentials.put("anna", demo); credentials.put("olga", demo); credentials.put(id, password);
        Class<?> wrapper = Class.forName("com.purrmatch.data.match.StoredMatches");
        write(destination, wrapper.getConstructor(type("MatchData"), Map.class).newInstance(data, credentials));
    }
}
