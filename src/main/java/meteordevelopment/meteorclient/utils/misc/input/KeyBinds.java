/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.misc.input;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class KeyBinds {
    private static final String CATEGORY = "Meteor Client";

    public static KeyBinding OPEN_GUI = new KeyBinding("key.meteor-client.open-gui", InputUtil.Type.KEYSYM, -1, CATEGORY);
    public static KeyBinding OPEN_COMMANDS = new KeyBinding("key.meteor-client.open-commands", InputUtil.Type.KEYSYM, -1, CATEGORY);

    private KeyBinds() {
    }

    public static KeyBinding[] apply(KeyBinding[] binds) {
        return binds;
    }
}
