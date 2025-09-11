#!/usr/bin/env python

import json
import sys
import time
from enum import Enum
from typing import Optional

import anyio
import screeninfo
from anyio.abc import SocketStream


def parse_line(line: str) -> dict | None:
    try:
        data = json.loads(line.strip())
    except json.JSONDecodeError as e:
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


async def connect(host: str = "localhost", port: int = 8088):
    screen = screeninfo.get_monitors()[0]
    screen_width  = screen.width
    screen_height = screen.height

    try:
        async with await anyio.connect_tcp(host, port) as client:
            await process_events(client, screen_width, screen_height)

    except* ConnectionRefusedError:
        pass

    except* OSError as eg:
        pass

    except* ConnectionAbortedError as eg:
        pass

    except* KeyboardInterrupt:
        pass

    except* anyio.EndOfStream:
        pass


async def process_events(imotions_server: SocketStream,
                         screen_width: int, screen_height: int):
    line_acc: str = ''
    while True:
        response = await imotions_server.receive()
        if not response:
            continue

        #print(f">>> {response=}")

        # NOTE: here response cannot be empty
        lines: list[str] = response.decode().splitlines(keepends=True)
        #print(f">>> {lines=}")
        if line_acc:
            lines[0] = line_acc + lines[0]
            line_acc = ''
        if lines[-1][-1] != '\n':
            line_acc = lines.pop()

        #print(f"--- {lines=}")

        for line in lines:
            #print(f">>> {line=}", flush=True)
            data = parse_line(line)
            #print(f"... {data=}", flush=True)
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


if __name__ == "__main__":
    try:
        anyio.run(main)
    except KeyboardInterrupt:
        pass
    except ConnectionAbortedError as e:
        pass
    finally:
        pass
