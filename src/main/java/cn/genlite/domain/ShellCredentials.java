package cn.genlite.domain;

import java.util.UUID;

/** Presence is independent of parsing: damaged packed items must never become fresh templates. */
public record ShellCredentials(boolean present, UUID source, UUID token) {
    public static ShellCredentials parse(boolean present, String source, String token) {
        return new ShellCredentials(present, uuid(source), uuid(token));
    }
    private static UUID uuid(String value) {
        try {return value==null?null:UUID.fromString(value);} catch(IllegalArgumentException e) {return null;}
    }
    public boolean matches(UUID consumedSource, UUID consumedToken) {
        return consumedSource!=null && consumedToken!=null && consumedSource.equals(source) && consumedToken.equals(token);
    }
}
