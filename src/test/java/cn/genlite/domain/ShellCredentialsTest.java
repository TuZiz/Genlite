package cn.genlite.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ShellCredentialsTest {
    private final UUID source=UUID.randomUUID(),token=UUID.randomUUID();
    @Test void freshCreativeTemplateHasNoCredentials() {
        var fresh=ShellCredentials.parse(false,null,null);
        assertFalse(fresh.present());assertNull(fresh.source());assertNull(fresh.token());
        assertFalse(fresh.matches(null,null));
    }
    @Test void validPackedItemMatchesOnlyItsExactConsumedCredential() {
        var packed=ShellCredentials.parse(true,source.toString(),token.toString());
        assertTrue(packed.present());assertTrue(packed.matches(source,token));
        assertFalse(packed.matches(source,UUID.randomUUID()));assertFalse(packed.matches(UUID.randomUUID(),token));
    }
    @Test void changedHandOrMovedItemCannotMatchAnotherShellOrTemplate() {
        assertFalse(ShellCredentials.parse(false,null,null).matches(source,token));
        assertFalse(ShellCredentials.parse(true,UUID.randomUUID().toString(),UUID.randomUUID().toString()).matches(source,token));
        // A cloned stack or a copy moved to another inventory slot still carries the spent credential.
        assertTrue(ShellCredentials.parse(true,source.toString(),token.toString()).matches(source,token));
    }
    @Test void corruptOrWrongTypedTagsStayPackedAndCannotFallThroughToFreshPlacement() {
        var corrupt=ShellCredentials.parse(true,"broken-source","broken-token");
        assertTrue(corrupt.present());assertNull(corrupt.source());assertNull(corrupt.token());assertFalse(corrupt.matches(source,token));
        assertTrue(ShellCredentials.parse(true,null,null).present());
    }
    @Test void partialCredentialsRemainPackedAndAreRejected() {
        var a=ShellCredentials.parse(true,source.toString(),null);
        var b=ShellCredentials.parse(true,null,token.toString());
        assertTrue(a.present());assertTrue(b.present());assertFalse(a.matches(source,token));assertFalse(b.matches(source,token));
    }
}
