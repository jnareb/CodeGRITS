#!/usr/bin/env python

import json
import sys
import time
from enum import Enum
from typing import Optional

import anyio
import screeninfo
from anyio.abc import SocketStream


VERBOSE=False


def parse_line(line: str) -> dict | None:
    try:
        data = json.loads(line.strip())
    except json.JSONDecodeError as e:
        if VERBOSE:
            print(f'error decoding line {line}:<br>\n{e}<br>\n', file=sys.stderr)
        data = None

    return data


def filter_data(data: dict,
                device_name: Optional[str] = None,
                sample_name: Optional[str] = 'EyeData') -> dict | None:
    if (('DeviceName' not in data) or
        ('SampleName' not in data)):
        return None  # it is an error, it should not happen

    if ((device_name is not None and data['DeviceName'] != device_name) or
        (sample_name is not None and data['SampleName'] != sample_name)):
        return None

    return data


class EyeSide(str, Enum):
    LEFT  = 'Left'
    RIGHT = 'Right'


def is_gaze_valid(data: dict, eye: EyeSide) -> bool:
    try:
        return data[f'Gaze{eye.value}X'] != -1 and data[f'Gaze{eye.value}Y'] != -1
    except KeyError:
        return False


async def connect(host: str = 'localhost', port: int = 8088):
    screen = screeninfo.get_monitors()[0]
    screen_width  = screen.width
    screen_height = screen.height

    try:
        async with await anyio.connect_tcp(host, port) as client:
            await process_events(client, screen_width, screen_height)

    except* ConnectionRefusedError:
        if VERBOSE:
            print('Could not connect to iMotions API server.', file=sys.stderr)

    except* OSError as eg:
        if VERBOSE:
            print('Problem trying to connect to iMotions API server.', file=sys.stderr)

    except* ConnectionAbortedError as eg:
        if VERBOSE:
            print('Connection to iMotions API server aborted.', file=sys.stderr)

    except* KeyboardInterrupt:
        if VERBOSE:
            print('Keyboard interrupt during connect().', file=sys.stderr)

    except* anyio.EndOfStream:
        if VERBOSE:
            print('End of stream from iMotions API server.', file=sys.stderr)


async def process_events(imotions_server: SocketStream,
                         screen_width: int, screen_height: int):
    line_acc: str = ''
    while True:
        response = await imotions_server.receive()
        if not response:
            continue

        # NOTE: here response cannot be empty
        lines: list[str] = response.decode().splitlines(keepends=True)

        if line_acc:
            lines[0] = line_acc + lines[0]
            line_acc = ''
        if lines[-1][-1] != '\n':
            line_acc = lines.pop()


        for line in lines:
            data = parse_line(line)
            if data is None:
                continue

            data = filter_data(data, sample_name='EyeData')
            if data is None:
                continue

            # see pythonScriptTobii and pythonScriptMouse in CodeGRITS source code
            message = '{}; {}, {}, {}, {}, {}; {}, {}, {}, {}, {}'.format(
                # timestamp in milliseconds
                round(time.time() * 1000),  # or `data['GazeTime'] * 1000`
                # left eye
                data['GazeLeftX'] / screen_width  if data['GazeLeftX'] != -1 else -1.0,
                data['GazeLeftY'] / screen_height if data['GazeLeftY'] != -1 else -1.0,
                1.0 if (data['GazeLeftX'] != -1 and data['GazeLeftY'] != -1) else 0.0,
                data['PupilLeft'],
                1.0 if data['PupilLeft'] != -1 else 0.0,
                # right eye
                data['GazeRightX'] / screen_width  if data['GazeRightX'] != -1 else -1.0,
                data['GazeRightY'] / screen_height if data['GazeRightY'] != -1 else -1.0,
                1.0 if (data['GazeRightX'] != -1 and data['GazeRightY'] != -1) else 0.0,
                data['PupilRight'],
                1.0 if data['PupilRight'] != -1 else 0.0,
            )
            print(message, flush=True)


async def main():
    await connect()


if __name__ == '__main__':
    try:
        anyio.run(main)
    except KeyboardInterrupt:
        if VERBOSE:
            print('Keyboard interrupt.', file=sys.stderr)
    except ConnectionAbortedError as e:
        if VERBOSE:
            print('Connection aborted.', file=sys.stderr)
    finally:
        pass
