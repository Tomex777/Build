#!/usr/bin/env python3
"""Reject green device runs that omit requested acceptance methods."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

EXPECTED = {
    "AnnieImageViewerTest": {"tappingChatImageOpensGenuineScreenWidthViewer"},
    "AnnieImageEditorTest": {"editsRotateRealBitmapWithoutChangingOriginal", "squareCropProducesSquareImageWithoutAlteringOriginal", "horizontalMirrorChangesPixelOrientation"},
    "AnnieVoiceRecordingTest": {"lockedRecordingPausesMicrophoneAndSendsOneAacFile"},
    "AnnieVoicePauseResumeTest": {"pauseSeekAndResumePreservesPlaybackPosition"},
    "AnnieGeckoTabRestoreTest": {"recreationRestoresSelectedNormalTabAndDesktopModeWithoutPrivateTabs"},
    "AnnieGeckoDownloadHistoryTest": {"completedFileReopensFromRecreatedHistoryWithoutNetworkOrRequestSecrets"},
    "AnnieGeckoDownloadManagerTest": {
        "progressIsClampedAndSafeForUnknownLength",
        "cancelAndCompleteTransitionsDoNotReviveCancelledFile",
        "actualDownloadPersistsBytesAndCanBeReadThroughReturnedUri",
        "storageFailureDoesNotLeaveIncompleteDownload",
    },
    "AnnieGeckoBrowserEndToEndTest": {"geckoNavigatesBackAndDownloadsTheActualResponseForOfflineOpening"},
}


def verify(root, name):
    cases = {}
    for report in Path(root).rglob("*.xml"):
        try:
            document = ET.parse(report).getroot()
        except ET.ParseError:
            continue
        for case in document.iter("testcase"):
            if case.get("classname") == f"com.tomex777.annie.{name}":
                cases[case.get("name")] = case
    missing = EXPECTED[name] - cases.keys()
    failures = [method for method, case in cases.items()
                if any(case.find(tag) is not None for tag in ("failure", "error", "skipped"))]
    if missing or failures:
        print(f"ERROR {name}: missing={sorted(missing)}, unsuccessful={failures}")
        return False
    print(f"PASS {name}: all {len(EXPECTED[name])} required methods executed successfully")
    return True


if __name__ == "__main__":
    sys.exit(0 if verify(sys.argv[1], sys.argv[2]) else 1)
