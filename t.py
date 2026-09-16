import os
import re

# Đường dẫn tới thư mục chứa code
FOLDER_PATH = r"/home/oibanoi874/Downloads/mod-project"

# Regex tìm dòng JADX INFO
pattern = re.compile(r".*JADX INFO: loaded.*\n?")

for root, dirs, files in os.walk(FOLDER_PATH):
    for file in files:
        if file.endswith((".java", ".kt")):
            file_path = os.path.join(root, file)
            with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
                content = f.read()

            new_content = re.sub(pattern, "", content)

            with open(file_path, "w", encoding="utf-8") as f:
                f.write(new_content)

print("Đã xóa xong tất cả comment JADX INFO!")
