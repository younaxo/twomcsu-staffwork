package su.twomc.staffwork.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import su.twomc.staffwork.util.Validation;

/** Проверяет, что все объявленные permission-узлы соответствуют формату tmc.staffwork.[категория]... */
class PermissionsFormatTest {

    @Test
    void everyDeclaredPermissionMatchesRequiredFormat() throws IllegalAccessException {
        List<String> nodes = new ArrayList<>();
        for (Field field : Permissions.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                nodes.add((String) field.get(null));
            }
        }
        assertFalse(nodes.isEmpty(), "В Permissions должны быть объявлены узлы прав");
        for (String node : nodes) {
            assertTrue(Validation.isValidPermissionNode(node), "Некорректный формат permission: " + node);
            assertTrue(node.startsWith("tmc.staffwork."), "Permission должен начинаться с tmc.staffwork.: " + node);
        }
    }
}
