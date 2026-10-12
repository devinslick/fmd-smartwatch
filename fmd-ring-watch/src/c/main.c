#include <pebble.h>

#define ACTION_COUNT 5

static const char *s_commands[ACTION_COUNT] = {
    "ring",
    "locate",
    "lock",
    "flash",
    "nodisturb on",
};

static const char *s_labels[ACTION_COUNT] = {
    "Ring",
    "Locate",
    "Lock",
    "Flash",
    "DND on",
};

static Window *s_window;
static TextLayer *s_action;
static TextLayer *s_status;
static char s_status_buf[128];
static int s_selected = 0;
static bool s_waiting = false;

static void update_status(const char *msg) {
  snprintf(s_status_buf, sizeof(s_status_buf), "%s", msg);
  text_layer_set_text(s_status, s_status_buf);
}

static void noack_callback(void *data) {
  if (s_waiting) {
    update_status("Sent\n(no ack)");
  }
}

static void outbox_failed_callback(DictionaryIterator *iter, AppMessageResult reason, void *context) {
  update_status("send failed");
}

static void outbox_sent_callback(DictionaryIterator *iter, void *context) {
  s_waiting = false;
  // Phone-side companion accepted the command.
  update_status("Fired!");
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
  text_layer_set_text(s_action, s_labels[s_selected]);
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
    update_status(t->value->cstring);
  }
}

static void inbox_dropped_callback(AppMessageResult reason, void *context) {
  update_status("reply dropped");
}

static void window_load(Window *window) {
  Layer *window_layer = window_get_root_layer(window);
  GRect bounds = layer_get_bounds(window_layer);

  s_action = text_layer_create(GRect(0, 30, bounds.size.w, 50));
  text_layer_set_text(s_action, s_labels[0]);
  text_layer_set_font(s_action, fonts_get_system_font(FONT_KEY_BITHAM_42_BOLD));
  text_layer_set_text_alignment(s_action, GTextAlignmentCenter);
  layer_add_child(window_layer, text_layer_get_layer(s_action));

  s_status = text_layer_create(GRect(8, 92, bounds.size.w - 16, 110));
  text_layer_set_text(s_status, "UP/DN: pick\nSELECT: fire");
  text_layer_set_font(s_status, fonts_get_system_font(FONT_KEY_GOTHIC_18));
  text_layer_set_text_alignment(s_status, GTextAlignmentCenter);
  layer_add_child(window_layer, text_layer_get_layer(s_status));
}

static void window_unload(Window *window) {
  text_layer_destroy(s_action);
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
