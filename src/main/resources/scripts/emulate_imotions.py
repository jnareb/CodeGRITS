#!/usr/bin/env python
import socket
import sys

import anyio
from tqdm.asyncio import tqdm


# how many samples per second to send
sample_rate_Hz = 60.000
# path to a file with data to send over TCP socket
file_name = 'traces/iMotions_traces/imotions-events_API-2025-06-12.ascii'


async def events_from_file(client, tg):
    peer = None
    try:
        peer = client.extra(anyio.abc.SocketAttribute.remote_address)
        print(f"Client connected: {peer}")

        async with await anyio.open_file(file_name, mode='rt') as fd:
            line: str
            async for line in tqdm(fd):
                # don't strip() line here, we want to send it as-is
                # end-of-line character is used to find out if we got a partial line
                # or split multiple lines sent in one packet (received at once)
                await client.send(bytes(line, encoding='utf-8'))
                await anyio.sleep(1.0/sample_rate_Hz)

            print("Finished sending data.")

        print("Closed file.")
        #tg.cancel_scope.cancel()
        await client.aclose()
        print("Closed connection.")

    except* (anyio.EndOfStream, ConnectionError):
        # Client disconnected unexpectedly
        print(f"Client {peer} disconnected.")
    except* (anyio.BrokenResourceError, ConnectionAbortedError):
        print(f"Client {peer} aborted connection.")
    finally:
        print(f"Connection with {peer} closed.")


async def main():
    host = "localhost"
    port = 8088
    print(f"Starting server at {host}:{port}...")
    print(f"{host} is {socket.gethostbyname_ex(host)[2]}")
    print(f"Sending data from {file_name!r}\n"
          f"with sample rate {sample_rate_Hz} Hz (it/s), period/delay of {1.0/sample_rate_Hz:.3f} s (s/it),...")
    listener = await anyio.create_tcp_listener(local_port=port)
    print(f"Server started on port {port}. Listening for connections...")

    async with anyio.create_task_group() as tg:
        await listener.serve(handler=lambda client: events_from_file(client, tg),
                             task_group=tg)

    print("Done serving.")


if __name__ == "__main__":
    if len(sys.argv) > 1:
        file_name = sys.argv[1]

    try:
        anyio.run(main)
    except KeyboardInterrupt:
        print("Server stopped by user (Ctrl+C).")
