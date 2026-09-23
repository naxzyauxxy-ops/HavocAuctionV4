package net.eclipse.havocauction.economy;

import net.eclipse.havocauction.HavocAuction;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Thin Vault wrapper so the rest of the plugin never touches Vault directly. */
public class EconomyHook {

    private final HavocAuction plugin;
    private Economy economy;

    public EconomyHook(HavocAuction plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("Vault is not installed - HavocAuction needs it for the economy.");
            return false;
        }
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            plugin.getLogger().severe("No Vault economy provider found (install EssentialsX Economy, CMI, etc).");
            return false;
        }
        economy = provider.getProvider();
        plugin.getLogger().info("Hooked into Vault economy: " + economy.getName());
        return true;
    }

    public boolean isReady() {
        return economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }

    public double balance(OfflinePlayer player) {
        return economy == null ? 0.0D : economy.getBalance(player);
    }

    public boolean has(OfflinePlayer player, double amount) {
        return economy != null && economy.has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (economy == null || amount <= 0) return amount <= 0;
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (economy == null || amount <= 0) return amount <= 0;
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }
}
