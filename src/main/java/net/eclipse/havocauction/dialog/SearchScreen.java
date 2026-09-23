package net.eclipse.havocauction.dialog;

import net.eclipse.havocauction.HavocAuction;
import net.eclipse.havocauction.ui.ScreenModel;
import net.eclipse.havocauction.util.Text;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Private text input. The value travels from the client to the server with the button
 * click, so it never appears in chat or in any chat-logging plugin.
 */
public class SearchScreen extends Screen {

    private static final String KEY = "query";

    private final String initial;
    private final Consumer<String> onSubmit;
    private final Runnable onBack;

    public SearchScreen(HavocAuction plugin, Player player, String initial,
                        Consumer<String> onSubmit, Runnable onBack) {
        super(plugin, player);
        this.initial = initial == null ? "" : initial;
        this.onSubmit = onSubmit;
        this.onBack = onBack;
    }

    @Override
    protected String configPath() {
        return "SEARCH";
    }

    @Override
    public String title() {
        return titleFrom(Map.of());
    }

    @Override
    public List<String> bodyLines() {
        return resolve(lines("BODY"), Map.of());
    }

    @Override
    public List<ScreenModel.Input> inputs() {
        return List.of(new ScreenModel.Input(KEY, string("INPUT-LABEL", "&fSearch"), initial));
    }

    @Override
    public ScreenModel.Button exitButton() {
        return backButton("BACK", Map.of(), onBack);
    }

    @Override
    public List<ScreenModel.Button> buttons() {
        return List.of(
                configButton("CONFIRM", Map.of(), responses -> {
                    String value = responses.text(KEY);
                    click();
                    if (value == null || value.isBlank()) {
                        if (isBedrock()) tell(plugin.message("SEARCH-USE-COMMAND"));
                        onSubmit.accept(initial);
                        return;
                    }
                    onSubmit.accept(value.trim());
                }),
                configButton("CLEAR", Map.of(), responses -> {
                    click();
                    onSubmit.accept("");
                })
        );
    }
}
