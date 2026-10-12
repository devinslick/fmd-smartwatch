#include <pebble.h>

#define ACTION_COUNT 15

static const char *s_commands[ACTION_COUNT] = {
    "ring",
    "ring long",
    "locate",
    "locate last",
    "locate gps",
    "stats",
    "lock",
    "flash",
    "ringermode silent",
    "ringermode normal",
    "nodisturb on",
    "nodisturb off",
    "bluetoothscan",
    "camera front",
    "camera back",
};

static const char *s_titles[ACTION_COUNT] = {
    "Ring",
    "Ring",
    "Locate",
    "Locate",
    "Locate",
    "Stats",
    "Lock",
    "Flash",
    "Ringer",
    "Ringer",
    "DND",
    "DND",
    "Scan",
    "Camera",
    "Camera",
};

static const char *s_subs[ACTION_COUNT] = {
    "",
    "3min",
    "",
    "last known",
    "GPS",
    "",
    "",
    "",
    "silent",
    "normal",
    "on",
    "off",
    "Bluetooth",
    "Front",
    "Back",
};

static Window *s_window;
static Layer *s_action_layer;
static TextLayer *s_status;
static char s_status_buf[128];
static int s_selected = 0;
static bool s_waiting = false;
static AppTimer *s_status_timer = NULL;

static const char *STATUS_HELP = "UP/DN: pick\nSELECT: fire";

static void update_status(const char *msg) {
  snprintf(s_status_buf, sizeof(s_status_buf), "%s", msg);
  text_layer_set_text(s_status, s_status_buf);
}

// Terminal statuses clear back to the help text after a short hold instead of
// staying on screen forever.
static void status_reset_cb(void *data) {
  s_status_timer = NULL;
  update_status(STATUS_HELP);
}

static void update_status_final(const char *msg) {
  update_status(msg);
  if (s_status_timer) {
    app_timer_cancel(s_status_timer);
  }
  s_status_timer = app_timer_register(5000, status_reset_cb, NULL);
}

static void noack_callback(void *data) {
  if (s_waiting) {
    update_status_final("Sent\n(no ack)");
  }
}

static void outbox_failed_callback(DictionaryIterator *iter, AppMessageResult reason, void *context) {
  update_status_final("send failed");
}

static void outbox_sent_callback(DictionaryIterator *iter, void *context) {
  s_waiting = false;
  // Phone-side companion accepted the command.
  update_status_final("Fired!");
}

static void send_command(void) {
  update_status("Sending...");
  DictionaryIterator *out;
  AppMessageResult result = app_message_outbox_begin(&out);
  if (result != APP_MSG_OK) {
    update_status("busy");
    return;
  }
  dict_write_cstring(out, MESSAGE_KEY_Cmd, s_commands[s_selected]);
  result = app_message_outbox_send();
  if (result != APP_MSG_OK) {
    update_status("send err");
  } else {
    s_waiting = true;
    app_timer_register(1500, noack_callback, NULL);
  }
}

static void render_selection(void) {
  layer_mark_dirty(s_action_layer);
}

static void select_click_handler(ClickRecognizerRef recognizer, void *context) {
  send_command();
}

static void up_click_handler(ClickRecognizerRef recognizer, void *context) {
  s_selected = (s_selected + ACTION_COUNT - 1) % ACTION_COUNT;
  render_selection();
}

static void down_click_handler(ClickRecognizerRef recognizer, void *context) {
  s_selected = (s_selected + 1) % ACTION_COUNT;
  render_selection();
}

static void click_config_provider(void *context) {
  window_single_click_subscribe(BUTTON_ID_SELECT, select_click_handler);
  window_single_click_subscribe(BUTTON_ID_UP, up_click_handler);
  window_single_click_subscribe(BUTTON_ID_DOWN, down_click_handler);
}

static void inbox_received_callback(DictionaryIterator *iter, void *context) {
  Tuple *t = dict_find(iter, MESSAGE_KEY_Result);
  if (t && t->value->cstring[0] != '\0') {
    s_waiting = false;
    // Copy immediately: tuple strings are only valid inside this callback.
    update_status_final(t->value->cstring);
  }
}

static void inbox_dropped_callback(AppMessageResult reason, void *context) {
  update_status_final("reply dropped");
}

// Largest system font at which the label still fits on a single line.
// heights[] is each font's approximate single-line height: measuring with
// word-wrap, a used height at or below it means "no wrap" -> this font wins.
static GFont pick_font(const char *text, int16_t max_w, GSize *used_out) {
  const char *keys[] = {
    FONT_KEY_BITHAM_42_BOLD, FONT_KEY_GOTHIC_28_BOLD,
    FONT_KEY_GOTHIC_24_BOLD, FONT_KEY_GOTHIC_18_BOLD,
  };
  const int heights[] = { 50, 34, 30, 24 };
  GFont font = NULL;
  GSize used = { 0, 0 };
  for (size_t i = 0; i < sizeof(keys) / sizeof(keys[0]); i++) {
    font = fonts_get_system_font(keys[i]);
    used = graphics_text_layout_get_content_size(
        text, font, GRect(0, 0, max_w, 2000),
        GTextOverflowModeWordWrap, GTextAlignmentLeft);
    if (used.h <= heights[i]) {
      break;
    }
  }
  *used_out = used;
  return font;
}

static void draw_action(Layer *layer, GContext *ctx) {
  GRect bounds = layer_get_bounds(layer);
  const int16_t max_w = bounds.size.w - 8;
  const char *title = s_titles[s_selected];
  const char *sub = s_subs[s_selected];

  GSize t_used = { 0, 0 };
  GFont t_font = pick_font(title, max_w, &t_used);

  GFont sub_font = fonts_get_system_font(FONT_KEY_GOTHIC_18);
  GSize sub_used = { 0, 0 };
  if (sub[0] != '\0') {
    sub_used = graphics_text_layout_get_content_size(
        sub, sub_font, GRect(0, 0, max_w, 2000),
        GTextOverflowModeTrailingEllipsis, GTextAlignmentCenter);
  }

  const int16_t block_h = t_used.h + (sub_used.h ? 2 + sub_used.h : 0);
  const int16_t y0 = bounds.origin.y + (bounds.size.h - block_h) / 2;
  graphics_draw_text(ctx, title, t_font,
                     GRect(4, y0, max_w, t_used.h + 2),
                     GTextOverflowModeTrailingEllipsis, GTextAlignmentCenter, NULL);
  if (sub_used.h) {
    graphics_draw_text(ctx, sub, sub_font,
                       GRect(4, y0 + t_used.h + 2, max_w, sub_used.h + 2),
                       GTextOverflowModeTrailingEllipsis, GTextAlignmentCenter, NULL);
  }
}

static void window_load(Window *window) {
  Layer *window_layer = window_get_root_layer(window);
  GRect bounds = layer_get_bounds(window_layer);

  s_action_layer = layer_create(GRect(0, 16, bounds.size.w, 76));
  layer_set_update_proc(s_action_layer, draw_action);
  layer_add_child(window_layer, s_action_layer);

  s_status = text_layer_create(GRect(8, 92, bounds.size.w - 16, 110));
  text_layer_set_text(s_status, STATUS_HELP);
  text_layer_set_font(s_status, fonts_get_system_font(FONT_KEY_GOTHIC_18));
  text_layer_set_text_alignment(s_status, GTextAlignmentCenter);
  layer_add_child(window_layer, text_layer_get_layer(s_status));
}

static void window_unload(Window *window) {
  if (s_status_timer) {
    app_timer_cancel(s_status_timer);
    s_status_timer = NULL;
  }
  layer_destroy(s_action_layer);
  text_layer_destroy(s_status);
}

static void init(void) {
  s_window = window_create();
  window_set_click_config_provider(s_window, click_config_provider);
  window_set_window_handlers(s_window, (WindowHandlers) {
    .load = window_load,
    .unload = window_unload,
  });
  app_message_register_inbox_received(inbox_received_callback);
  app_message_register_inbox_dropped(inbox_dropped_callback);
  app_message_register_outbox_failed(outbox_failed_callback);
  app_message_register_outbox_sent(outbox_sent_callback);
  app_message_open(app_message_inbox_size_maximum(), app_message_outbox_size_maximum());
  window_stack_push(s_window, true);
}

static void deinit(void) {
  window_destroy(s_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}
