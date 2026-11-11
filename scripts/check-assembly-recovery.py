"""Verify native payment files after a confirmed process kill; inspect the live world UUID separately."""
import argparse
import json
import shutil
from pathlib import Path
from read_nbt import load

parser = argparse.ArgumentParser()
parser.add_argument("checkpoint", choices=["PAYMENT_DURABLE", "ENTITY_DURABLE", "ACTIVATED"])
parser.add_argument("revision", type=int)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
prefix = root / ".tools" / f"crash-{args.checkpoint.lower()}-r{args.revision}"
meta = json.loads(Path(str(prefix) + "-meta.json").read_text(encoding="utf-8"))
termination = json.loads(Path(str(prefix) + "-termination.json").read_text(encoding="utf-8-sig"))
if termination["checkpoint"] != args.checkpoint or termination["revision"] != args.revision or termination["shutdownCommand"]:
    raise ValueError("Missing matching process-termination evidence")
before = load(Path(str(prefix) + "-journal.nbt"))
recovered_journal = Path(str(prefix) + "-recovered-journal.nbt")
recovered_player = Path(str(prefix) + "-recovered-player.dat")
shutil.copyfile(root / "run/world/data/legacyoftheforce/speeder_assembly" / (meta["owner"] + ".nbt"), recovered_journal)
shutil.copyfile(root / "run/world/playerdata" / (meta["owner"] + ".dat"), recovered_player)
after = load(recovered_journal)
player = load(recovered_player)
if after["phase"] != "DONE":
    raise ValueError("Assembly recovery has not completed")
comparison = dict(after, phase=before["phase"])
if comparison != before:
    raise ValueError("Recovery changed the immutable transaction or vehicle identity")
if player["Inventory"] != before["after"]["Inventory"]:
    raise ValueError("Native player inventory differs from the single paid image")
receipt = player["neoforge:attachments"]["legacyoftheforce:speeder_assembly_receipt"]
if receipt != before["after"]["receipt"] or receipt["revision"] != args.revision or "RootVehicle" in player:
    raise ValueError("Native receipt changed or player contains a duplicate vehicle save")
summary = dict(meta, recoveredPhase="DONE", transactionUnchanged=True, nativeInventoryPaid=True,
               receiptUnchanged=True, rootVehicleAbsent=True, worldEntityCheck="separate live-server UUID check required")
Path(str(prefix) + "-recovered.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
print(json.dumps(summary))
