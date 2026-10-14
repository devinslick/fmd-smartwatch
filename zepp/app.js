import { MessageBuilder } from "../shared/message";

// FMD Remote — Zepp OS device app (draft).
//
// IMPORTANT (transport reality, verified against Zepp OS 3 docs): there is
// NO local channel from a Zepp OS app to a third-party Android app. The
// watch's link is owned by the Zepp app; the only sanctioned outbound path
// is the app-side service's fetch() over the internet. This app therefore
// sends commands through a small self-hosted relay (see ../relay/) which
// forwards them to the phone bridge. Cloud-mediated BY DESIGN — that is
// the only option on this platform today.

const commands = [
  { label: "Ring", cmd: "ring" },
  { label: "Ring long", cmd: "ring long" },
  { label: "Locate", cmd: "locate" },
  { label: "Locate last", cmd: "locate last" },
  { label: "Locate GPS", cmd: "locate gps" },
  { label: "Lock", cmd: "lock" },
  { label: "Camera front", cmd: "camera front" },
  { label: "Camera back", cmd: "camera back" },
  { label: "Ringer silent", cmd: "ringermode silent" },
  { label: "Ringer normal", cmd: "ringermode normal" }
];

const messageBuilder = new MessageBuilder();
messageBuilder.connect();

AppGestureStyle({
  statusBar: { show: false },
  gesture: {SwipeUpDown: "exit" }
});

const TITLE_HEIGHT = 48;
const ITEM_HEIGHT = 60;

const list = hmUI.createWidget(hmUI.widget.SCROLL_LIST, {
  x: 0,
  y: 0,
  w: 192,
  h: 490,
  item_space: 8,
  item_config: [
    {
      type_id: 1,
      item_bg_color: 0x1a1a1a,
      item_bg_radius: 12,
      text_view: [
        { x: 12, y: 0, w: 168, h: ITEM_HEIGHT, key: "label", color: 0xffffff, text_size: 30 }
      ]
    }
  ],
  item_config_count: 1,
  data_array: commands.map((c) => ({ type_id: 1, label: c.label })),
  item_click: (widget, index) => {
    const entry = commands[index];
    hmApp.showToast({ text: "Sent: " + entry.label });
    messageBuilder.request({
      method: "GET",
      path: "/command",
      data: { cmd: entry.cmd }
    });
  }
});
