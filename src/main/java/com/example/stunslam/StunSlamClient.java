package com.example.stunslam;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

public class StunSlamClient implements ClientModInitializer {
    // Delay (in ticks) between swapping and hitting. Raise if hits don't land at full strength.
    private static final int AXE_DELAY = 1;
    private static final int MACE_DELAY = 1;

    private enum Stage { IDLE, SWAP_AXE, HIT_AXE, SWAP_MACE, HIT_MACE }

    private static KeyBinding key;
    private static Stage stage = Stage.IDLE;
    private static Entity target;
    private static int timer;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("stunslam", "main"));
        key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.stunslam.activate", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, category));
        ClientTickEvents.END_CLIENT_TICK.register(StunSlamClient::tick);
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) { stage = Stage.IDLE; return; }

        // Key press arms the combo (ignored while one is already running)
        while (key.wasPressed()) {
            if (stage == Stage.IDLE) start(mc);
        }

        if (stage == Stage.IDLE) return;
        if (target == null || !target.isAlive()) { stage = Stage.IDLE; return; }
        if (timer > 0) { timer--; return; }

        switch (stage) {
            case SWAP_AXE -> { select(mc, findAxe(mc)); timer = AXE_DELAY; stage = Stage.HIT_AXE; }
            case HIT_AXE -> { attack(mc); stage = Stage.SWAP_MACE; }
            case SWAP_MACE -> { select(mc, findMace(mc)); timer = MACE_DELAY; stage = Stage.HIT_MACE; }
            case HIT_MACE -> { attack(mc); stage = Stage.IDLE; target = null; } // off until re-activated
            default -> {}
        }
    }

    private static void start(MinecraftClient mc) {
        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY
            || !(((EntityHitResult) hit).getEntity() instanceof LivingEntity e)) {
            msg(mc, "Aim at a target first.");
            return;
        }
        if (findAxe(mc) < 0) { msg(mc, "No axe in hotbar."); return; }
        if (findMace(mc) < 0) { msg(mc, "No mace in hotbar."); return; }
        target = e;
        timer = 0;
        stage = Stage.SWAP_AXE;
    }

    private static int findAxe(MinecraftClient mc) {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).isIn(ItemTags.AXES)) return i;
        return -1;
    }

    private static int findMace(MinecraftClient mc) {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).isOf(Items.MACE)) return i;
        return -1;
    }

    private static void select(MinecraftClient mc, int slot) {
        if (slot >= 0) mc.player.getInventory().setSelectedSlot(slot);
    }

    private static void attack(MinecraftClient mc) {
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private static void msg(MinecraftClient mc, String s) {
        mc.player.sendMessage(Text.literal("[StunSlam] " + s), true);
    }
}
