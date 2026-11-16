"""Independent native socket protocol oracle; no client marker alone certifies rejection."""
import argparse
import json
import uuid
from pathlib import Path
from read_nbt import load


def uid(parts):
    return str(uuid.UUID(int=sum((part & 0xffffffff) << (96 - 32 * index) for index, part in enumerate(parts))))


def verify(repo, run):
    result = json.loads((repo / f'.tools/core-protocol-{run}-result.json').read_text(encoding='utf-8-sig'))
    assert result['observed'] and result['serverExitCode'] == 0
    cases = ['Admin', 'Guest', 'Reconnect', 'WrongDirection', 'Malformed', 'Mismatch']
    assert [item['case'] for item in result['clients']] == cases
    world = repo / f'run-core-protocol-{run}/world'
    server = load(world / 'validation/core-protocol-server.nbt')
    assert server['schemaVersion'] == 1 and len(server['before']) == len(server['after']) == 2
    assert server['before'] == server['after'], 'Native inventory/RPG/game mode changed'
    owners = [uid(actor['owner']) for actor in server['before']]
    assert len(set(owners)) == 2
    for actor in server['before']:
        assert actor['Inventory'] == [] and actor['gameType'] == 0 and not actor['invulnerable']
        progress = actor['progress']
        assert progress['level'] == 1 and progress['totalXpEarned'] == 0 and progress['rewardRevision'] == 0
        saved = load(world / f'playerdata/{uid(actor["owner"])}.dat')
        assert saved['Inventory'] == actor['Inventory']
        assert saved['neoforge:attachments']['legacyoftheforce:player_progress'] == progress
    events = server['events']
    assert [item['sequence'] for item in events] == [1, 66, 2**63-1, 0]
    assert [item['stage'] for item in events] == ['BASELINE', 'BASELINE', 'MAX', 'REJOIN']
    assert all(uid(item['owner']) == owners[0] and item['serverThread'] for item in events)
    assert events[1]['tick'] - events[0]['tick'] >= 20
    assert server['initialAdminEndpoint'].startswith('/127.0.0.1:')
    assert server['rejoinedAdminEndpoint'].startswith('/127.0.0.1:')
    assert server['initialAdminEndpoint'] != server['rejoinedAdminEndpoint']
    assert [item['case'] for item in server['cases']] == ['WrongDirection', 'Malformed']
    for attack in server['cases']:
        assert attack['before'] == attack['after'] and uid(attack['before']['owner']) == owners[1]
        assert attack['before']['progress'] == server['before'][1]['progress']
    wrong = [item for item in server['arrivals'] if item['case'] == 'WrongDirection']
    assert len(wrong) == 1 and wrong[0]['id'] == 'legacyoftheforce:progress_snapshot'
    assert wrong[0]['decodedType'] == 'DiscardedPayload' and uid(wrong[0]['owner']) == owners[1]
    assert not any(item['case'] == 'Malformed' for item in server['arrivals']), 'Malformed VarLong reached packet handler'
    proofs = {}
    pids = [server['pid']]
    for item in result['clients']:
        case = item['case']
        profile = repo / f'run-core-protocol-client-{case}-{run}'
        assert Path(item['profile']).resolve() == profile.resolve()
        assert item['exitCode'] == 0 and not item['forced']
        proof = load(profile / 'validation/core-protocol-client.nbt')
        assert proof['schemaVersion'] == 1 and proof['case'] == case and proof['pid'] == item['pid']
        pids.append(item['pid'])
        assert proof['played'] == (case != 'Mismatch')
        assert proof['versionInjected'] == (case == 'Mismatch')
        log = (profile / 'logs/latest.log').read_text(encoding='utf-8')
        assert 'CORE_PROTOCOL_CLIENT_PASS' in log and 'CORE_PROTOCOL_CLIENT_FAILED' not in log
        from PIL import Image
        with Image.open(profile / f'screenshots/core-protocol-{case}.png') as capture:
            assert capture.width >= 800 and capture.height >= 450
            capture.verify()
        proofs[case] = proof
    assert len(set(pids)) == 7
    baseline = [1, 1, 0, -1, -(2**63)] + list(range(2, 66)) + [65]
    assert proofs['Guest']['sent'] == baseline and proofs['Guest']['readyCount'] == 0 and proofs['Guest']['deniedCount'] == 1
    assert proofs['Admin']['sent'] == baseline + [66, 67, 2**63-1, 2**63-1, 2**63-2]
    assert proofs['Admin']['readyCount'] == 3 and proofs['Admin']['deniedCount'] == 1
    assert proofs['Reconnect']['sent'] == [0] and proofs['Reconnect']['readyCount'] == 1 and proofs['Reconnect']['deniedCount'] == 0
    for case in ['WrongDirection', 'Malformed']:
        assert proofs[case]['wireWritten'] and 0 < proofs[case]['wireBytes'] <= 128
        assert proofs[case]['sent'] == [] and proofs[case]['readyCount'] == proofs[case]['deniedCount'] == 0
    assert proofs['WrongDirection']['result'] == 'direction_ignored'
    assert proofs['Malformed']['result'] == 'decoder_disconnected' and proofs['Malformed']['disconnectReason']
    assert proofs['Mismatch']['result'] == 'rejected_before_play'
    log = (world.parent / 'logs/latest.log').read_text(encoding='utf-8')
    assert 'CORE_PROTOCOL_SERVER_PASS' in log and 'CORE_PROTOCOL_SERVER_FAILED' not in log and 'All dimensions are saved' in log
    assert 'Received legacyoftheforce:progress_snapshot on the SERVER' in log and 'refusing to decode' in log
    assert 'VarLong too big' in log
    # Version rejection must be proven by native negotiation, in addition to the client fault receipt.
    mismatch_log = (repo / f'run-core-protocol-client-Mismatch-{run}/logs/latest.log').read_text(encoding='utf-8')
    assert '999' in mismatch_log and ('version' in mismatch_log.lower() or 'incompatible' in mismatch_log.lower())
    assert log.count('ProtocolAdmin joined the game') == 2 and log.count('ProtocolGuest joined the game') == 3
    return {'run': run, 'scope': 'native_loopback_versioned_diagnostic_protocol', 'clients': 6,
            'acceptedSequences': [1, 66, 2**63-1, 0], 'unchangedProfiles': owners, 'gameplayOrLoadCertification': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('run')
    args = parser.parse_args()
    print(json.dumps(verify(Path(__file__).resolve().parents[1], args.run), indent=2))
