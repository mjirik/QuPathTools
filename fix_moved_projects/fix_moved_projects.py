from pathlib import Path
import json
import shutil
import datetime
import traceback
from loguru import logger

import typer

def fix_moved_projects(actual_path:Path, old_path:Path, new_path:Path, dry_run:bool=False):
    """Fix paths in project.qproj files after moving the project to another location.

    :param qproj_file:  path to project.qproj file
    :param old_path:  old path to replace
    :param new_path: new path to replace
    :param dry_run: if True, do not save the file
    :return:
    """
    # actual_path = r"C:/Users/Jirik/my_bc_data/"
    # old_path = r"file:///C:/Users/Jirik/SynologyBC/"
    # new_path = r"file:/C:/Users/Jirik/my_bc_data/"
    # dry_run = True

    actual_path = Path(actual_path)
    new_path = Path(new_path)
    old_path = Path(old_path)

    # list(actual_path.glob("**/project.qproj"))
    qproj_files = list(actual_path.glob("**/project.qpproj"))
    print(f"Found {len(qproj_files)} project.qproj files")

    for qproj_file in qproj_files:
        try:
            process_qproj_file(qproj_file, old_path, new_path, dry_run=dry_run)
        except json.JSONDecodeError:
            logger.exception(f"JSON decode error in {qproj_file}: {traceback.format_exc()}")


def process_qproj_file(qproj_file:Path, old_path:Path, new_path:Path, dry_run:bool=False):
    """
    Process one qproj file
    :param qproj_file:  path to project.qproj file
    :param old_path:  old path to replace
    :param new_path: new path to replace
    :param dry_run: if True, do not save the file
    :return:
    """

    print(f"Processing {qproj_file}")
    with open(qproj_file, "r") as f:
        data = json.load(f)

    changed = False
    for j in range(len(data["images"])):
        KeyError
        try:
            old_uri = Path(data["images"][j]["serverBuilder"]["uri"])
        except KeyError:
            logger.exception(f"KeyError in {qproj_file}")
            continue
        if old_path in old_uri.parents:  # is subpath
            relative_path = old_uri.absolute().relative_to(old_path.absolute())
            new_uri_ith = new_path / relative_path
            print(f"{old_uri} --> {new_uri_ith}")
            data["images"][j]["serverBuilder"]["uri"] = str(new_uri_ith)
            changed = True
        else:
            print(f"Expected old location does not match. Skipping image file {old_uri}.")

    if not dry_run:
        if changed:
            # make backup of the original file before overwriting
            timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
            shutil.copyfile(qproj_file, qproj_file.with_suffix(f".qproj.{timestamp}.backup"))

            # shutil.copyfile(qproj_file, qproj_file.with_suffix(".qproj.bak"))

            print(f"Saving {qproj_file}")
            with open(qproj_file, "w") as f:
                json.dump(data, f, indent=3)


if __name__ == "__main__":
    typer.run(fix_moved_projects)