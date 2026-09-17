from pathlib import Path

path = Path('sora-ci/patch_music_listening_events.py')
source = path.read_text()
old = """    '''        if (previous >= 0) playQueueIndex(previous)\\n    }\\n\\n    fun seekToFraction''',\n    '''        if (previous >= 0) {\\n            if (previous != currentIndex) emitSkipIfStarted(currentTrack)\\n            playQueueIndex(previous)\\n        }\\n    }\\n\\n    fun seekToFraction''',"""
new = """    '''        playQueueIndex(previous)\\n    }\\n\\n    fun seekToFraction''',\n    '''        if (previous != currentIndex) emitSkipIfStarted(currentTrack)\\n        playQueueIndex(previous)\\n    }\\n\\n    fun seekToFraction''',"""
if source.count(old) != 1:
    raise SystemExit(f'v2 previous-skip matcher repair expected 1 match, found {source.count(old)}')
source = source.replace(old, new, 1)
exec(compile(source, str(path), 'exec'))
