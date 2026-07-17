#!/usr/bin/env python3
"""Generate TermFast app icon — terminal style with pixel font.

Design:
  - Dark terminal window with rounded corners
  - "Term" in large bold pixel font
  - ">_" command prompt — bold, positioned right and up
  - Blinking cursor block to the right of ">_"
  - Classic terminal green
"""
from PIL import Image, ImageDraw, ImageFont

SIZE = 512
PADDING = 55

# Colors
BG_COLOR = (20, 20, 30)
BORDER_COLOR = (60, 60, 80)
GREEN = (102, 217, 74)  # softer green #66D94A — not too harsh
WHITE = (255, 255, 255)  # pure white for prompt
TITLEBAR_COLOR = (35, 35, 50)
DOT_RED = (243, 139, 168)
DOT_YELLOW = (249, 226, 175)
DOT_GREEN = (166, 227, 161)

img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

# Terminal window
win_x0 = PADDING
win_y0 = PADDING
win_x1 = SIZE - PADDING
win_y1 = SIZE - PADDING
radius = 36

draw.rounded_rectangle(
    [win_x0, win_y0, win_x1, win_y1],
    radius=radius,
    fill=BG_COLOR,
    outline=BORDER_COLOR,
    width=3,
)

# Title bar
titlebar_h = 44
draw.rounded_rectangle(
    [win_x0, win_y0, win_x1, win_y0 + titlebar_h],
    radius=radius,
    fill=TITLEBAR_COLOR,
)
draw.rectangle(
    [win_x0, win_y0 + titlebar_h - radius, win_x1, win_y0 + titlebar_h],
    fill=TITLEBAR_COLOR,
)

# Traffic-light dots
dot_y = win_y0 + titlebar_h // 2
dot_r = 7
dot_spacing = 26
dot_start_x = win_x0 + 22
for i, color in enumerate([DOT_RED, DOT_YELLOW, DOT_GREEN]):
    cx = dot_start_x + i * dot_spacing
    draw.ellipse(
        [cx - dot_r, dot_y - dot_r, cx + dot_r, dot_y + dot_r],
        fill=color,
    )

# Pixel font
vt323_path = "scripts/fonts/VT323.ttf"

def load_font(size):
    try:
        return ImageFont.truetype(vt323_path, size)
    except Exception:
        return ImageFont.load_default()

def draw_bold_text(draw, xy, text, fill, font, bold_x=3, bold_y=3):
    """Draw text with simulated bold by offsetting multiple times."""
    x, y = xy
    for dx in range(bold_x):
        for dy in range(bold_y):
            draw.text((x + dx, y + dy), text, fill=fill, font=font)

# Text area — moved right by half a character, up by half a character
font_size_text = 150
char_w_half = int(font_size_text * 0.275)  # half a character width
char_h_half = int(font_size_text * 0.25)   # half a character height
char_w_quarter = int(font_size_text * 0.14)  # quarter character width
char_h_quarter = int(font_size_text * 0.125) # quarter character height
text_x = win_x0 + 45 + char_w_half - char_w_quarter  # right by half, left by quarter = net right quarter
text_y = win_y0 + titlebar_h + 35 - char_h_half       # up by half a character

# "Term" — large and bold
font_term = load_font(font_size_text)
draw_bold_text(draw, (text_x, text_y), "Term", fill=GREEN, font=font_term, bold_x=3, bold_y=3)

# Measure "Term" for character width
bbox_term = draw.textbbox((0, 0), "Term", font=font_term)
term_w = bbox_term[2] - bbox_term[0]

# Prompt ">_" — on the next line below "Term"
# Moved right by one character (indented), up by half a character (closer to Term)
char_w = int(font_size_text * 0.55)  # one character width in VT323
half_char_h = int(font_size_text * 0.25)

char_w_quarter_prompt = int(150 * 0.14)  # quarter char width at prompt size
char_h_eighth = int(150 * 0.0625)        # eighth char height at prompt size

prompt_x = text_x + char_w - char_w_quarter_prompt  # right by one char, left by quarter
prompt_y = text_y + font_size_text - half_char_h + char_h_quarter + char_h_eighth  # down by eighth

font_prompt = load_font(170)  # bigger (was 150)
draw_bold_text(draw, (prompt_x, prompt_y), ">_", fill=WHITE, font=font_prompt, bold_x=8, bold_y=3)

# Blinking cursor block to the right of ">_"
bbox_prompt = draw.textbbox((0, 0), ">_", font=font_prompt)
prompt_w = bbox_prompt[2] - bbox_prompt[0]
cursor_x = prompt_x + prompt_w + 14
cursor_y_top = prompt_y + 110
cursor_y_bot = prompt_y + 150
cursor_w = 36
draw.rectangle(
    [cursor_x, cursor_y_top, cursor_x + cursor_w, cursor_y_bot],
    fill=WHITE,
)

out_path = "src-tauri/icons/app-icon.png"
img.save(out_path)
print(f"Icon saved to {out_path} ({SIZE}x{SIZE})")
