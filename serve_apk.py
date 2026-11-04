#!/usr/bin/env python3
import http.server
import socketserver
import os
import sys

PORT = 8080
DIRECTORY = os.path.abspath("android/app/build/outputs/apk/debug")

class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

if __name__ == "__main__":
    os.chdir(DIRECTORY)
    with socketserver.TCPServer(("", PORT), Handler) as httpd:
        print(f"Serving APKs at http://0.0.0.0:{PORT}")
        httpd.serve_forever()

