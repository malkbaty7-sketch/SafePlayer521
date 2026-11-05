import os
from PIL import Image

src_img = "./app/src/main/res/drawable/safe_player_icon_1789310554671.jpg"
res_dir = "./app/src/main/res"

sizes = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192
}

try:
    img = Image.open(src_img)
    # create a square image (crop center)
    width, height = img.size
    min_dim = min(width, height)
    left = (width - min_dim) / 2
    top = (height - min_dim) / 2
    right = (width + min_dim) / 2
    bottom = (height + min_dim) / 2
    img = img.crop((left, top, right, bottom))

    for density, size in sizes.items():
        mipmap_dir = os.path.join(res_dir, f"mipmap-{density}")
        os.makedirs(mipmap_dir, exist_ok=True)
        
        # Standard icon
        resized = img.resize((size, size), Image.Resampling.LANCZOS)
        resized.save(os.path.join(mipmap_dir, "ic_launcher.png"), "PNG")
        
        # Round icon (mask with circle)
        mask = Image.new('L', (size, size), 0)
        from PIL import ImageDraw
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, size, size), fill=255)
        
        round_img = resized.copy()
        round_img.putalpha(mask)
        round_img.save(os.path.join(mipmap_dir, "ic_launcher_round.png"), "PNG")
        
    print("Icons generated successfully.")
except Exception as e:
    print(f"Error: {e}")
