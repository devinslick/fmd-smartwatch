import { MessageBuilder } from "../shared/message";

// App-side service: runs inside the Zepp phone app's sandbox. Receives
// command requests from the device app over MessageBuilder and relays them
// via fetch() to the self-hosted FMD relay (relay/relay.py). This is the
// ONLY way out of the Zepp sandbox — no intents, no local IPC exists.

const RELAY_URL = "https://fmd-relay.example.com"; // deploy relay/, set URL
const RELAY_TOKEN = "change-me";                   // must match relay config

AppSideService({
  onInit() {
    const messageBuilder = new MessageBuilder();
    messageBuilder.listen(() => {});
    messageBuilder.onCallRequest(({ requestId, data }) => {
      fetch({
        url: RELAY_URL + "/command",
        method: "POST",
        headers: {
          "content-type": "application/json"
        },
        body: JSON.stringify({ token: RELAY_TOKEN, cmd: data.cmd })
      })
        .then((response) => {
          messageBuilder.response(requestId, { ok: response.status === 200 });
        })
        .catch((error) => {
          messageBuilder.response(requestId, { ok: false, error: String(error) });
        });
    });
    messageBuilder.connect();
  }
});
