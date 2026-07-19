package me.foesio.foTeams.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class InventoryCodec {
    private InventoryCodec() {
    }

    public static String encode(List<ItemStack> items) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(items.size());
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode inventory", exception);
        }
    }

    public static List<ItemStack> decode(String input) {
        List<ItemStack> items = new ArrayList<>();
        if (input == null || input.isBlank()) {
            return items;
        }
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(input));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            int size = dataInput.readInt();
            for (int slot = 0; slot < size; slot++) {
                items.add((ItemStack) dataInput.readObject());
            }
            dataInput.close();
            return items;
        } catch (IOException | ClassNotFoundException | IllegalArgumentException exception) {
            return new ArrayList<>();
        }
    }
}
