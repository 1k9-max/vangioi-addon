package com.example.vangioi;

import com.example.vangioi.commands.AddItemCommand;
import com.example.vangioi.commands.ClearFarmerCommand;
import com.example.vangioi.commands.ClearWatererCommand;
import com.example.vangioi.commands.DelItemCommand;
import com.example.vangioi.commands.ItemListCommand;
import com.example.vangioi.modules.AlchemicalPillMaking;
import com.example.vangioi.modules.AutoMinigameModule;
import com.example.vangioi.modules.AutoAcceptModule;
import com.example.vangioi.modules.AutoPhobanModule;
import com.example.vangioi.modules.AutoCropFarmer;
import com.example.vangioi.modules.AutoCropWaterer;
import com.example.vangioi.modules.AutoBossModule;
import com.example.vangioi.modules.AutoFurnaceModule;
import com.example.vangioi.modules.AutoDropVanilla;
import com.example.vangioi.modules.ChestDropAllButton;
import com.example.vangioi.modules.GuiDumperModule;
import com.example.vangioi.modules.AutoFarm;
import com.example.vangioi.modules.AutoFish;
import com.example.vangioi.modules.AutoWalkStraightModule;
import com.example.vangioi.modules.ChatAutoResponder;
import com.example.vangioi.modules.FlyGotoModule;
import com.example.vangioi.modules.FlyToPlacementModule;
import com.example.vangioi.modules.LinhThaoLocations;
import com.example.vangioi.hud.ExperienceHud;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.hud.Hud;
import org.slf4j.Logger;

/**
 * Entrypoint cua addon. Duoc tro toi tu "entrypoints" -> "meteor" trong fabric.mod.json.
 * Khong co license/whitelist check, khong ghi file ra dia ngoai config module, khong spawn
 * process nao khac - chi la cac module tu dong hoa client-side thong thuong.
 */
public class AutoCropFarmerAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Van Gioi Addon");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Van Gioi Addon");
        Hud.get().register(ExperienceHud.INFO);

        // Modules
        Modules.get().add(new AutoCropFarmer());
        Modules.get().add(new AutoCropWaterer());
        Modules.get().add(new AutoBossModule());
        Modules.get().add(new AutoFurnaceModule());
        Modules.get().add(new AlchemicalPillMaking());
        Modules.get().add(new AutoPhobanModule());
        Modules.get().add(new AutoMinigameModule());
        Modules.get().add(new AutoFarm());
        Modules.get().add(new AutoFish());
        Modules.get().add(new LinhThaoLocations());
        Modules.get().add(new ChatAutoResponder());
        Modules.get().add(new FlyGotoModule());
        Modules.get().add(new FlyToPlacementModule());
        Modules.get().add(new AutoAcceptModule());
        Modules.get().add(new AutoWalkStraightModule());
        Modules.get().add(new AutoDropVanilla());
        Modules.get().add(new ChestDropAllButton());
        Modules.get().add(new GuiDumperModule());

        // Commands
        Commands.add(new ClearFarmerCommand());
        Commands.add(new ClearWatererCommand());
        Commands.add(new AddItemCommand());
        Commands.add(new DelItemCommand());
        Commands.add(new ItemListCommand());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.vangioi";
    }

    @Override
    public GithubRepo getRepo() {
        // Thay bang repo GitHub thuc te cua ban de Meteor co the check update.
        return new GithubRepo("your-username", "van-gioi-addon");
    }
}
