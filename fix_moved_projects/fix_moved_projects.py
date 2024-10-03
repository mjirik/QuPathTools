from pathlib import Path
import json
import shutil
import datetime
import traceback
from loguru import logger
import shutil
import tqdm
from typing import List
import copy

import typer

def fix_moved_projects_by_replace(actual_path:Path, old_strs:List[str], new_str:str, dry_run:bool=False):
    """Fix paths in project.qproj files after moving the project to another location.

    :param qproj_file:  path to project.qproj file
    :param old_strs:  list of old paths with same meaning like file:////, file://, file:/, etc.
    :param new_str: new path to replace
    :param dry_run: if True, do not save the file
    :return:
    """
    # actual_path = r"C:/Users/Jirik/my_bc_data/"
    # old_path = r"file:///C:/Users/Jirik/SynologyBC/"
    # new_path = r"file:/C:/Users/Jirik/my_bc_data/"
    # dry_run = True

    actual_path = Path(actual_path)

    # list(actual_path.glob("**/project.qproj"))
    qpproj_files = list(actual_path.glob("**/project.qpproj"))
    print(f"Found {len(qpproj_files)} project.qpproj files")

    # do logger with tqdm
    for qpproj_file in tqdm.tqdm(qpproj_files):
        process_qproj_file_by_replace(qpproj_file, old_strs, new_str, dry_run=dry_run)

# def fix_moved_projects(actual_path:Path, old_path:Path, new_path:Path, dry_run:bool=False):
#     """Fix paths in project.qproj files after moving the project to another location.
#
#     :param qproj_file:  path to project.qproj file
#     :param old_path:  old path to replace
#     :param new_path: new path to replace
#     :param dry_run: if True, do not save the file
#     :return:
#     """
#     # actual_path = r"C:/Users/Jirik/my_bc_data/"
#     # old_path = r"file:///C:/Users/Jirik/SynologyBC/"
#     # new_path = r"file:/C:/Users/Jirik/my_bc_data/"
#     # dry_run = True
#
#     actual_path = Path(actual_path)
#     new_path = Path(new_path)
#     old_path = Path(old_path)
#
#     # list(actual_path.glob("**/project.qproj"))
#     qproj_files = list(actual_path.glob("**/project.qpproj"))
#     print(f"Found {len(qproj_files)} project.qproj files")
#
#     # do logger with tqdm
#     for qproj_file in tqdm.tqdm(qproj_files):
#         process_qproj_file_by_replace(qproj_file, old_path, new_path, dry_run=dry_run)
#         # try:
#         #     # print(f"Processing {qproj_file}      {i+1}/{len(qproj_files)}")
#         # except json.JSONDecodeError:
#         #     logger.exception(f"JSON decode error in {qproj_file}: {traceback.format_exc()}")


def process_qproj_file_by_replace(qpproj_file:Path, old_strs:List[str], new_str:str, dry_run:bool=False):
    qpproj_file = Path(qpproj_file)

    # make_backup_qpproj_file(qpproj_file, dry_run)
    # open qpproj file
    assert qpproj_file.exists(), f"File {qpproj_file} does not exist."
    with open(qpproj_file, "r") as file:
        try:
            data = file.read()
        except OSError as e:
            tqdm.tqdm.write(f"Error reading {qpproj_file}. Check if the file is offline in Synology Drive Client.")
            # logger.exception(f"Error reading {qpproj_file}")
            # raise e
            return
        data_orig = copy.deepcopy(data)

    # with open(qpproj_file, "r") as f:
    #     data = f.read()
    #     data_orig = copy.deepcopy(data)

    for old_str in old_strs:
        # find and replace all occurences of old_str with new_str and count changes
        data = data.replace(old_str, new_str)
    # is there any change?
    if data != data_orig:
        # tqdm.write(f"    {old_str} --> {new_str}")
        if not dry_run:
            datetime_str = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
            backup_fn = qpproj_file.with_suffix(f".backup.{datetime_str}.qpproj")
            with open(backup_fn, "w") as f:
                f.write(data_orig)

            with open(qpproj_file, "w") as f:
                f.write(data)


    else:
        pass
        # tqdm.tqdm.write(f"Skipping {qpproj_file}")


def make_backup_qpproj_file(qpproj_file, dry_run):
    datetime_str = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    # copy the file
    if not dry_run:
        backup_fn = qpproj_file.with_suffix(f".backup.{datetime_str}.qpproj")
        # logger.debug(f"Copying {qpproj_file} to {backup_fn}")
        assert qpproj_file.exists(), f"File {qpproj_file} does not exist."

        with open(qpproj_file, "r") as f:
            data = f.read()
            # write to backup file
            with open(backup_fn, "w") as f:
                f.write(data)
        # if not backup_fn.exists():
        #     shutil.copyfile(str(qpproj_file), str(backup_fn))


def process_qproj_file_by_uri_anlaysis(qpproj_file:Path, old_path:Path, new_path:Path, dry_run:bool=False):
    """
    Process one qproj file
    :param qpproj_file:  path to project.qproj file
    :param old_path:  old path to replace
    :param new_path: new path to replace
    :param dry_run: if True, do not save the file
    :return:
    """
    qpproj_file = Path(qpproj_file)

    # copy the file
    if not dry_run:
        datetime_str = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        src_fn = qpproj_file
        dst_fn = qpproj_file.with_suffix(f".bak.{datetime_str}.qproj")
        logger.debug(f"Copying {src_fn} to {dst_fn}")
        shutil.copyfile(src_fn, dst_fn)

    with open(qpproj_file, "r") as f:
        data = json.load(f)

    with_ok = 0
    with_error = 0
    with_nomatch = 0
    changed = False
    for j in range(len(data["images"])):
        KeyError
        try:
            old_uri = Path(data["images"][j]["serverBuilder"]["uri"])
        except KeyError:
            logger.exception(f"KeyError in {qpproj_file}")
            with_error += 1
            continue
        if old_path in old_uri.parents:  # is subpath
            relative_path = old_uri.absolute().relative_to(old_path.absolute())
            new_uri_ith = new_path / relative_path
            print(f"    {old_uri} --> {new_uri_ith}")
            data["images"][j]["serverBuilder"]["uri"] = str(new_uri_ith)
            changed = True
        else:
            print(f"Expected old location does not match. Skipping image file {old_uri}.")
            with_nomatch += 1

    if not dry_run:
        if changed:
            # make backup of the original file before overwriting
            timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
            backup_path = qpproj_file.with_suffix(f".backup.{timestamp}.qpproj")
            logger.debug(f"Saving {qpproj_file} as {backup_path} .")
            shutil.copyfile(qpproj_file, backup_path)

            # shutil.copyfile(qproj_file, qproj_file.with_suffix(".qproj.bak"))

            print(f"Saving {qpproj_file}")
            with open(qpproj_file, "w") as f:
                json.dump(data, f, indent=3)

    # print(f"with_ok: {with_ok}, with_error: {with_error}, with_nomatch: {with_nomatch}")

# if __name__ == "__main__":
#     typer.run(fix_moved_projects)