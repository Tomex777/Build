"""Host-owned byte fixture survives target app/instrumentation process death."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import time

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        size = 4 * 1024 * 1024 if self.path.startswith('/resume') else 65536
        data = bytes((i * 37 + 11) & 255 for i in range(size))
        offset = int(self.headers.get('Range', 'bytes=0-').split('=')[1].split('-')[0])
        if offset >= size:
            self.send_response(416)
            self.end_headers()
            return
        self.send_response(206 if offset else 200)
        self.send_header('Content-Type', 'application/octet-stream')
        self.send_header('Content-Disposition', 'attachment; filename="resume.blorp"' if size > 65536 else 'attachment; filename="sample.blorp"')
        self.send_header('ETag', '"annie-proof-v1"')
        self.send_header('Content-Length', str(size - offset))
        if offset:
            self.send_header('Content-Range', f'bytes {offset}-{size-1}/{size}')
        self.end_headers()
        try:
            for start in range(offset, size, 32768):
                self.wfile.write(data[start:start+32768])
                self.wfile.flush()
                if size > 65536:
                    time.sleep(.1)
        except (BrokenPipeError, ConnectionResetError):
            pass

ThreadingHTTPServer(('0.0.0.0', 18765), Handler).serve_forever()
