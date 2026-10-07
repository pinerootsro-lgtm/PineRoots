/* Keeps the arrival page readable without signal once a guest has opened it.
   Pages and text files: network first (so edits show up), cache when offline.
   Photos: cache first. The video is left to the network. */
var CACHE = "pineroots-sosire-v1";
var CORE = [
  "./",
  "./index.html",
  "./favicon.svg",
  "./continut/date.txt",
  "./continut/sosire.txt",
  "./continut/reguli.txt",
  "./continut/drum.txt",
  "./continut/zona.txt",
  "./continut/restaurante.txt",
  "./media/poarta-cutie.jpg",
  "./media/usa-cheie.jpg",
  "./media/poarta-poster.jpg"
];

self.addEventListener("install", function (e) {
  e.waitUntil(caches.open(CACHE).then(function (c) { return c.addAll(CORE); }).then(function () { return self.skipWaiting(); }));
});

self.addEventListener("activate", function (e) {
  e.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(keys.filter(function (k) { return k !== CACHE; }).map(function (k) { return caches.delete(k); }));
    }).then(function () { return self.clients.claim(); })
  );
});

function withTimeout(promise, ms) {
  return new Promise(function (resolve, reject) {
    var t = setTimeout(function () { reject(new Error("timeout")); }, ms);
    promise.then(function (v) { clearTimeout(t); resolve(v); }, function (err) { clearTimeout(t); reject(err); });
  });
}

self.addEventListener("fetch", function (e) {
  var req = e.request;
  if (req.method !== "GET") return;
  var url = new URL(req.url);
  if (url.origin !== self.location.origin) return;
  if (/\.mp4$/i.test(url.pathname)) return;

  if (/\.(jpe?g|png|svg|webp)$/i.test(url.pathname)) {
    e.respondWith(caches.match(req).then(function (hit) {
      return hit || fetch(req).then(function (res) {
        if (res.ok) { var copy = res.clone(); caches.open(CACHE).then(function (c) { c.put(req, copy); }); }
        return res;
      });
    }));
    return;
  }

  e.respondWith(
    withTimeout(fetch(req), 5000).then(function (res) {
      if (res.ok) { var copy = res.clone(); caches.open(CACHE).then(function (c) { c.put(req, copy); }); }
      return res;
    }).catch(function () {
      return caches.match(req, { ignoreSearch: true }).then(function (hit) {
        return hit || (req.mode === "navigate" ? caches.match("./index.html") : Response.error());
      });
    })
  );
});
