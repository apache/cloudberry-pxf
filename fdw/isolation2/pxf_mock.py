import sys
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from http.client import HTTPConnection
from urllib.parse import urlparse, parse_qs

PXF_PORT=5889
print_headers = False

class Metadata:

    class MetadataItem:
        def __init__(self, length, value):
            self.length = length
            self.value = value

        def __str__(self):
            return f"<MetadataItem: {self.value} ({self.length})>"

    def __init__(self, metadata):
        self.metadata = metadata
        self.items = []
        self._parse()

    def _parse(self):
        # Read metadata item length from binary form
        i = 0
        while i < len(self.metadata):
            length = self.metadata[i:i+4]
            length = int.from_bytes(length, byteorder='little')

            i = i+4
            value = self.metadata[i:i+length]
            self.items.append(Metadata.MetadataItem(length, value))
            i = i+length

    def sorted(self):
        return sorted(self.items, key=lambda item: item.value)

class MockPXFHandler(BaseHTTPRequestHandler):

    def __init__(self, request, client_address, server):
        super().__init__(request, client_address, server)


    def log_message(self, format, *args):
        # Override log message to avoid printing datetime, so result will be reproducible
        print(format % args)

    def _log_request(self):
        # Parse URL
        parsed_path = urlparse(self.path)
        query_params = parse_qs(parsed_path.query)

        print("\n--- Incoming Request ---")
        print(f"Method: {self.command}")
        print(f"Path: {parsed_path.path}")
        print(f"Query Params: {query_params}")

        # Log headers
        if print_headers:
            print("\nHeaders:")
            for key, value in self.headers.items():
                print(f"{key}: {value}")


    def _log_body(self):
        content_length = self.headers.get('Content-Length')
        if content_length:
            length = int(content_length)
            body = self.rfile.read(length).decode('utf-8', errors='ignore')
            print("\nContent-Length: " + str(length) + "\nBody ():")
            print(body)

    def _send_ok(self, message):
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.end_headers()
        self.wfile.write(message)

    def do_GET(self):
        self._log_request()
        parsed_path = urlparse(self.path)
        # Shutdown endpoint
        if parsed_path.path == "/shutdown":
            print("Shutting down...")
            self._send_ok(b"Shutting down...")

            # shutdown must be called from another thread
            threading.Thread(target=self.server.shutdown, daemon=True).start()
            return

        self._send_ok(b"t0,1")

    def read_chunked(self):
        ret = []
        if self.headers.get("Expect", "") == "100-continue":
            for sline in self.rfile:
                if  sline == b'0\r\n':
                    break

                if sline.strip() == b'':
                    continue

                length = int(sline, 16)

                sline = self.rfile.read(length)
 
                if length != len(sline):
                    print("WARNING: invalid message length")
                ret.append(sline)
        return b''.join(ret)


    def do_POST(self):
        parsed_path = urlparse(self.path)

        if parsed_path.path == "/pxf/v1/write":
            self.read_chunked()
            # Send some test metadata            
            metadata = "Some test metadata for " + self.headers.get("X-GP-SEGMENT-ID", "<invalid segment>")
            self._send_ok(metadata.encode('utf-8'))
            return

        if parsed_path.path == "/pxf/v1/commit":
            self._log_request()
            self._log_body()
            raw_metadata = self.read_chunked()
            metadata = Metadata(raw_metadata)
            for item in metadata.sorted():
                print(item)

        # Send response
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.end_headers()
        self.wfile.write(b"OK")

    def do_PUT(self):
        self.do_POST()

def run(server_class=HTTPServer, handler_class=MockPXFHandler, port=PXF_PORT):
    server_address = ('', port)
    httpd = server_class(server_address, handler_class)

    print(f"Starting mock PXF server on port {port}...")

    httpd.serve_forever()
    sys.exit(0)


if __name__ == "__main__":
    run()
