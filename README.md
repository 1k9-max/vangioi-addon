# Auto Crop Farmer (Meteor Client Addon)

Addon cho Meteor Client (Fabric, Minecraft 1.21.4, Java 21) tu dong hoa quy trinh trong lai
va giam sat mot vung cay trong (mac dinh nham vao Pitcher Crop / Pitcher Plant).

## Thanh phan

- **Module `AutoCropFarmer`** (`com.example.autocropfarmer.modules.AutoCropFarmer`)
  State machine 6 buoc: WAITING_ITEM_1 -> WAITING_ITEM_2 -> SELECT_POS_1 -> SELECT_POS_2
  -> AUTO_PLANTING -> MONITORING. Xem javadoc dau file de biet chi tiet tung buoc.
- **Command `.clear-farmer`** (alias `.reset-farmer`)
  (`com.example.autocropfarmer.commands.ClearFarmerCommand`) - goi `forceReset()` de xoa
  toan bo du lieu tam va dua module ve trang thai ban dau.

## Build

Yeu cau: JDK 21.

```bash
./gradlew build
```

File .jar ket qua nam trong `build/libs/`.

## Truoc khi build lan dau

Mo `gradle.properties` va kiem tra dong `meteor_version` khop voi build Meteor Client
thuc te cho 1.21.4 duoc publish tai:
https://maven.meteordev.org/#/releases/meteordevelopment/meteor-client

Meteor thay doi so hieu phien ban theo tung dot release/snapshot, nen gia tri mac dinh
trong file nay co the can cap nhat truoc khi Gradle resolve duoc dependency.

## Luu y quan trong ve co che AUTO_PLANTING / MONITORING

Logic o buoc 5 va 6 duoc viet dua tren mo ta yeu cau (Armor Stand an danh dau vi tri,
theo doi thay doi BlockState de nhan biet cay da lon/bien doi). Day la co che dac thu
cua tung server/plugin (khong phai vanilla Minecraft thuan), nen ban co the can:

- Dieu chinh `armor-stand-search-radius` trong settings cua module cho phu hop.
- Dieu chinh lai `handleMonitoring()` trong `AutoCropFarmer.java` neu server cua ban
  dung co che khac (vi du: NBT rieng, block khac, tag rieng, v.v).

## CI/CD

`.github/workflows/build.yml` build project bang JDK 21 tren moi push/PR vao `main`,
va upload file .jar thanh artifact cua workflow run.
