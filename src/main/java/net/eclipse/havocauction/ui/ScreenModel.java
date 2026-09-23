package net.eclipse.havocauction.ui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * The UI-agnostic description of a screen.
 *
 * Screens describe what they want shown - a title, some lines, some inputs, some buttons -
 * and a renderer turns that into either a Minecraft dialog or a chest menu. Keeping one
 * description means a feature is written once and appears in both, rather than two
 * implementations that drift apart.
 */
public final class ScreenModel {

    /** Values the player typed, however the renderer collected them. */
    public interface Responses {
        String text(String key);

        Responses EMPTY = key -> null;
    }

    @FunctionalInterface
    public interface Action {
        void run(Responses responses);
    }

    /**
     * One button. {@code icon} is only used by the chest renderer; dialogs ignore it.
     * A null action means "just close".
     */
    public record Button(String key, String label, List<String> tooltip,
                         ItemStack icon, Material fallbackIcon, Action action) {

        public static Button of(String key, String label, List<String> tooltip,
                                ItemStack icon, Material fallbackIcon, Action action) {
            return new Button(key, label, tooltip == null ? List.of() : tooltip,
                    icon, fallbackIcon, action);
        }
    }

    /** A text field. The chest renderer collects these through a chat prompt. */
    public record Input(String key, String label, String initial) {
    }

    private ScreenModel() {
    }
}
