package com.example.autocropfarmer;

import com.example.autocropfarmer.commands.AddItemCommand;
import com.example.autocropfarmer.commands.ClearFarmerCommand;
import com.example.autocropfarmer.commands.ClearWatererCommand;
import com.example.autocropfarmer.commands.DelItemCommand;
import com.example.autocropfarmer.commands.ItemListCommand;
import com.example.autocropfarmer.modules.AlchemicalPillMaking;
import com.example.autocropfarmer.modules.AutoAcceptModule;
import com.example.autocropfarmer.modules.AutoCropFarmer;
import com.example.autocropfarmer.modules.AutoCropWaterer;
import com.example.autocropfarmer.modules.AutoDropVanilla;
import com.example.autocropfarmer.modules.AutoFarm;
import com.example.autocropfarmer.modules.AutoFish;
import com.example.autocropfarmer.modules.AutoWalkStraightModule;
import com.example.autocropfarmer.modules.ChatAutoResponder;
import com.example.autocropfarmer.modules.FlyGotoModule;
import com.example.autocropfarmer.modules.FlyToPlacementModule;
import com.example.autocropfarmer.modules.LinhThaoLocations;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

/**
 * Entrypoint cua addon. Duoc tro toi tu "entrypoints" -> "meteor" trong fabric.mod.json.
 * Khong co license/whitelist check, khong ghi file ra dia ngoai config module, khong spawn
 * process nao khac - chi la cac module tu dong hoa client-side thong thuong.
 */
public class AutoCropFarmerAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Auto Crop Farmer");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Auto Crop Farmer addon");

        // Modules
        Modules.get().add(new AutoCropFarmer());
        Modules.get().add(new AutoCropWaterer());
        Modules.get().add(new AlchemicalPillMaking());
        Modules.get().add(new AutoFarm());
        Modules.get().add(new AutoFish());
        Modules.get().add(new LinhThaoLocations());
        Modules.get().add(new ChatAutoResponder());
        Modules.get().add(new FlyGotoModule());
        Modules.get().add(new FlyToPlacementModule());
        Modules.get().add(new AutoAcceptModule());
        Modules.get().add(new AutoWalkStraightModule());
        Modules.get().add(new AutoDropVanilla());

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
        return "com.example.autocropfarmer";
    }

    @Override
    public GithubRepo getRepo() {
        // Thay bang repo GitHub thuc te cua ban de Meteor co the check update.
        return new GithubRepo("your-username", "autocropfarmer-addon");
    }
}
