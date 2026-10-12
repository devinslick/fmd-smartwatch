// FMD Ring - PebbleKit JS companion (optional HTTP path).
// Primary path is the Android-side companion receiver (AppMessage broadcast);
// this JS is a best-effort secondary: loopback HTTP to the FMD Ring app if
// it happens to be reachable. Errors are intentionally NOT reported to the
// watch so the companion path's behavior stays visible on screen.

var RING_URL = 'http://127.0.0.1:47613/ring';

Pebble.addEventListener('ready', function () {
  console.log('FMD Ring pkjs ready');
});

Pebble.addEventListener('appmessage', function (e) {
  if (e.payload.Ring) {
    console.log('Ring requested from watch');
    try {
      var xhr = new XMLHttpRequest();
      xhr.onload = function () {
        console.log('Loopback server said: ' + this.responseText);
      };
      xhr.onerror = function () {
        console.log('Loopback server unreachable (expected on serverless build)');
      };
      xhr.open('GET', RING_URL);
      xhr.send();
    } catch (e2) {
      console.log('XHR exception: ' + e2);
    }
  }
});
