package com.playerreinforcements.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.gui.toasts.IToast;
import net.minecraft.client.gui.toasts.TutorialToast;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Holds tutorial popups out of the toast renderer while the respawn controls are visible. */
@SideOnly(Side.CLIENT)
final class DeathScreenTutorialToasts {
    private static final Field VISIBLE = ReflectionHelper.findField(GuiToast.class, "visible", "field_191791_g");
    private static final Field QUEUE = ReflectionHelper.findField(GuiToast.class, "toastsQueue", "field_191792_h");
    private static final Field TOAST = ReflectionHelper.findField(VISIBLE.getType().getComponentType(),
            "toast", "field_193688_b");
    private static final Field VISIBILITY = ReflectionHelper.findField(TutorialToast.class,
            "visibility", "field_193674_f");

    private final List<TutorialToast> deferred = new ArrayList<>();
    private NetHandlerPlayClient connection;

    void beforeRender(Minecraft mc) {
        if (connection != mc.getConnection()) {
            deferred.clear();
            connection = mc.getConnection();
        }
        if (mc.currentScreen instanceof ReinforcementDeathScreen
                || mc.currentScreen instanceof ReinforcementDeathScreen.LeaveConfirmation) {
            deferTutorialToasts(mc.getToastGui());
        } else if (!deferred.isEmpty()) {
            if (connection != null) {
                for (TutorialToast toast : deferred) {
                    if (get(VISIBILITY, toast) == IToast.Visibility.SHOW) mc.getToastGui().add(toast);
                }
            }
            deferred.clear();
        }
    }

    private void deferTutorialToasts(GuiToast manager) {
        Object[] visible = (Object[]) get(VISIBLE, manager);
        for (int i = 0; i < visible.length; i++) {
            if (visible[i] == null) continue;
            Object toast = get(TOAST, visible[i]);
            if (toast instanceof TutorialToast) {
                deferred.add((TutorialToast) toast);
                visible[i] = null;
            }
        }

        // Vanilla keeps references to these same objects and can still update or hide them.
        Deque<?> queue = (Deque<?>) get(QUEUE, manager);
        Iterator<?> iterator = queue.iterator();
        while (iterator.hasNext()) {
            Object toast = iterator.next();
            if (toast instanceof TutorialToast) {
                deferred.add((TutorialToast) toast);
                iterator.remove();
            }
        }
    }

    private static Object get(Field field, Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not access Forge 1.12.2 toast state", failure);
        }
    }
}
