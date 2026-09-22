#!/usr/bin/env bash
set -euo pipefail

publish_one() {
  local path="$1"
  local msg="$2"
  for attempt in 1 2 3 4 5; do
    git pull --rebase origin "$TEST_BRANCH" || {
      git rebase --abort || true
      sleep 2
      continue
    }
    git add "$path"
    git commit -m "$msg" || true
    if git push origin HEAD:"$TEST_BRANCH"; then
      return 0
    fi
    sleep 2
  done
  return 1
}

PHONE_PATH="live-test/${SESSION_ID}.phone.enc.b64"
rm -f /tmp/phone.b64
for _ in $(seq 1 300); do
  git fetch origin "$TEST_BRANCH" >/dev/null 2>&1 || true
  if git show "origin/$TEST_BRANCH:$PHONE_PATH" > /tmp/phone.b64 2>/dev/null; then
    break
  fi
  sleep 2
done
test -s /tmp/phone.b64

base64 -d /tmp/phone.b64 > /tmp/phone.enc
openssl pkeyutl -decrypt \
  -inkey /tmp/runner_private.pem \
  -pkeyopt rsa_padding_mode:oaep \
  -pkeyopt rsa_oaep_md:sha256 \
  -in /tmp/phone.enc \
  -out /tmp/phone.txt

PHONE="$(tr -cd '0-9' < /tmp/phone.txt)"
[[ "$PHONE" =~ ^[0-9]{8,15}$ ]]
rm -f /tmp/phone.b64 /tmp/phone.enc /tmp/phone.txt

adb install -r "$COBALT_APK" >/dev/null
adb logcat -c || true
adb shell am start -W \
  -n com.tomex.cobaltandroid/.MainActivity \
  --es live_phone "$PHONE" \
  --es live_reply "Got your message - Cobalt Android receive/reply test passed." >/dev/null
unset PHONE

CODE=""
for _ in $(seq 1 120); do
  CODE="$(adb logcat -d -s CobaltPOC:I 2>/dev/null | sed -n 's/.*PAIRING_CODE_READY://p' | tail -n 1 | tr -d '\r\n' || true)"
  if [[ -z "$CODE" ]]; then
    adb shell uiautomator dump /sdcard/cobalt-live.xml >/dev/null 2>&1 || true
    adb pull /sdcard/cobalt-live.xml /tmp/cobalt-live.xml >/dev/null 2>&1 || true
    if [[ -s /tmp/cobalt-live.xml ]]; then
      CODE="$(python3 -c 'import re,xml.etree.ElementTree as E; r=E.parse("/tmp/cobalt-live.xml").getroot(); nodes=list(r.iter("node")); start=next((i for i,n in enumerate(nodes) if (n.attrib.get("text") or "").strip()=="PAIRING CODE"),-1); vals=[] if start<0 else [((n.attrib.get("text") or "").strip().replace(" ","").replace("-",""), n.attrib.get("class") or "") for n in nodes[start+1:]]; print(next((v for v,cls in vals if cls=="android.widget.TextView" and re.fullmatch(r"[A-Za-z0-9]{8}",v) and v not in {"COPYCODE","LINKED"}),""))' 2>/dev/null || true)"
    fi
  fi
  [[ -n "$CODE" ]] && break
  sleep 2
done

if [[ -z "$CODE" ]]; then
  echo "PAIRING_CODE_TIMEOUT"
  echo "=== Cobalt app log ==="
  adb logcat -d -v threadtime -s CobaltPOC:V AndroidRuntime:E 2>/dev/null | tail -n 400 || true
  echo "=== Visible app state ==="
  adb shell uiautomator dump /sdcard/cobalt-timeout.xml >/dev/null 2>&1 || true
  adb pull /sdcard/cobalt-timeout.xml /tmp/cobalt-timeout.xml >/dev/null 2>&1 || true
  cat /tmp/cobalt-timeout.xml 2>/dev/null || true
  exit 1
fi

printf '%s' "$CODE" | openssl pkeyutl -encrypt \
  -pubin \
  -inkey live-test/assistant_public.pem \
  -pkeyopt rsa_padding_mode:oaep \
  -pkeyopt rsa_oaep_md:sha256 \
  -out /tmp/code.enc
base64 -w0 /tmp/code.enc > "live-test/${SESSION_ID}.code.enc.b64"
unset CODE
rm -f /tmp/code.enc /tmp/cobalt-live.xml
publish_one "live-test/${SESSION_ID}.code.enc.b64" "Publish encrypted pairing code ${SESSION_ID}"

DONE=0
for _ in $(seq 1 450); do
  LOG="$(adb logcat -d -s CobaltPOC:I CobaltPOC:E 2>/dev/null || true)"
  if grep -q 'LIVE_TEST_DONE' <<<"$LOG"; then
    DONE=1
    break
  fi
  if grep -q 'LIVE_TEST_REPLY_FAILED' <<<"$LOG"; then
    break
  fi
  sleep 2
done

if [[ "$DONE" = "1" ]]; then
  printf 'passed\n' > "live-test/${SESSION_ID}.status.txt"
  publish_one "live-test/${SESSION_ID}.status.txt" "Record successful live test ${SESSION_ID}"
  adb shell am force-stop com.tomex.cobaltandroid || true
  exit 0
fi

printf 'failed\n' > "live-test/${SESSION_ID}.status.txt"
publish_one "live-test/${SESSION_ID}.status.txt" "Record failed live test ${SESSION_ID}"
adb shell am force-stop com.tomex.cobaltandroid || true
exit 1
