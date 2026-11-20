"""OP-11 (#137): đo ba ứng viên embedding cho điểm s của BR03 trên bộ dữ liệu giả.

Chạy trên CPU (máy chạy worker chưa biết có GPU). Ghi kết quả JSON để đưa vào docs/evidence/.
Giao thức và ngưỡng chốt trước khi chạy: docs/evidence/2026-11-20_op11_embedding.md.

    python eval.py --out results.json
"""

from __future__ import annotations

import argparse
import json
import os
import platform
import random
import re
import statistics
import sys
import time
from importlib.metadata import version
from pathlib import Path

HERE = Path(__file__).resolve().parent

# Cách mã hóa theo trang model của từng ứng viên. Mỗi model ghim theo commit Hugging Face của lần đo
# 20/11/2026 (docs/evidence/2026-11-20_op11_results.json), để chạy lại đo đúng bản đó.
CANDIDATES = [
    {"id": "intfloat/multilingual-e5-small", "revision": "614241f622f53c4eeff9890bdc4f31cfecc418b3",
     "query_prefix": "query: ", "passage_prefix": "passage: ", "segment": False},
    {"id": "bkai-foundation-models/vietnamese-bi-encoder", "revision": "84f9d9ada0d1a3c37557398b9ae9fcedcdf40be0",
     "query_prefix": "", "passage_prefix": "", "segment": True},
    {"id": "BAAI/bge-m3", "revision": "5617a9f61b028005a4858fdac845db406aefb181",
     "query_prefix": "", "passage_prefix": "", "segment": False},
]

BOOTSTRAP_ROUNDS = 1000
SEED = 20261120
LATENCY_REPEATS = 3
WARMUP = 3


def load_dataset() -> dict:
    data = json.loads((HERE / "dataset.json").read_text(encoding="utf-8"))
    tutor_ids = {t["id"] for t in data["tutors"]}
    for goal in data["goals"]:
        if goal["match"] not in tutor_ids:
            raise ValueError(f"{goal['id']} matches an unknown tutor")
    return data


def s_score(cos: float) -> float:
    """BR03: s = max(0, min(1, (cos + 1) / 2))."""
    return max(0.0, min(1.0, (cos + 1.0) / 2.0))


def rank_metrics(scores: list[list[float]], goals: list[dict], tutor_ids: list[str]) -> dict:
    """scores[g][t]: điểm của gia sư t cho mục tiêu g. Hòa điểm tính về phía bất lợi (thứ hạng xấu nhất)."""
    per_goal = []
    for g, goal in enumerate(goals):
        pos = tutor_ids.index(goal["match"])
        p = scores[g][pos]
        negatives = [scores[g][t] for t in range(len(tutor_ids)) if t != pos]
        rank = 1 + sum(1 for n in negatives if n >= p)
        auc = sum(1.0 if p > n else 0.5 if p == n else 0.0 for n in negatives) / len(negatives)
        per_goal.append({"goal": goal["id"], "rank": rank, "auc": auc, "pos": p, "neg_max": max(negatives),
                         "neg_mean": statistics.fmean(negatives)})
    return {
        "top1": statistics.fmean(1.0 if r["rank"] == 1 else 0.0 for r in per_goal),
        "top3": statistics.fmean(1.0 if r["rank"] <= 3 else 0.0 for r in per_goal),
        "mrr": statistics.fmean(1.0 / r["rank"] for r in per_goal),
        "auc": statistics.fmean(r["auc"] for r in per_goal),
        "mrr_ci95": bootstrap([1.0 / r["rank"] for r in per_goal]),
        "per_goal": per_goal,
    }


def bootstrap(values: list[float]) -> list[float]:
    rng = random.Random(SEED)
    means = sorted(statistics.fmean(rng.choices(values, k=len(values))) for _ in range(BOOTSTRAP_ROUNDS))
    return [means[int(0.025 * BOOTSTRAP_ROUNDS)], means[int(0.975 * BOOTSTRAP_ROUNDS) - 1]]


def tokens(text: str) -> list[str]:
    return re.findall(r"\w+", text.lower())


def lexical_baseline(data: dict) -> dict:
    """Mốc so sánh: cosine trên túi từ (âm tiết) không trọng số. Model phải hơn mốc này mới đáng dùng."""
    def vec(text: str) -> dict[str, int]:
        counts: dict[str, int] = {}
        for tok in tokens(text):
            counts[tok] = counts.get(tok, 0) + 1
        return counts

    def cos(a: dict[str, int], b: dict[str, int]) -> float:
        dot = sum(v * b.get(k, 0) for k, v in a.items())
        na = sum(v * v for v in a.values()) ** 0.5
        nb = sum(v * v for v in b.values()) ** 0.5
        return dot / (na * nb) if na and nb else 0.0

    tutor_vecs = [vec(t["text"]) for t in data["tutors"]]
    scores = [[cos(vec(g["text"]), tv) for tv in tutor_vecs] for g in data["goals"]]
    return rank_metrics(scores, data["goals"], [t["id"] for t in data["tutors"]])


def evaluate_model(candidate: dict, data: dict) -> dict:
    import numpy as np
    from huggingface_hub import HfApi, snapshot_download
    from sentence_transformers import SentenceTransformer

    revision = candidate["revision"]
    info = HfApi().model_info(candidate["id"], revision=revision)
    license_ = (info.card_data or {}).get("license") if info.card_data is not None else None
    path = Path(snapshot_download(candidate["id"], revision=revision))
    size_mb = sum(f.stat().st_size for f in path.rglob("*") if f.is_file()) / 2**20

    segment = None
    if candidate["segment"]:
        from pyvi import ViTokenizer
        segment = ViTokenizer.tokenize

    def prepare(text: str, prefix: str) -> str:
        return prefix + (segment(text) if segment else text)

    started = time.perf_counter()
    model = SentenceTransformer(candidate["id"], revision=revision, device="cpu")
    load_s = time.perf_counter() - started

    goals = [prepare(g["text"], candidate["query_prefix"]) for g in data["goals"]]
    tutors = [prepare(t["text"], candidate["passage_prefix"]) for t in data["tutors"]]
    g_emb = model.encode(goals, normalize_embeddings=True)
    t_emb = model.encode(tutors, normalize_embeddings=True)
    cos = (np.asarray(g_emb) @ np.asarray(t_emb).T).tolist()
    s = [[s_score(c) for c in row] for row in cos]
    metrics = rank_metrics(s, data["goals"], [t["id"] for t in data["tutors"]])

    # Độ trải của s: BR03 cộng s với các thành phần khác, nên s dồn vào một khoảng hẹp thì ít tác dụng.
    pos = [r["pos"] for r in metrics["per_goal"]]
    neg = [v for g, row in enumerate(s) for t, v in enumerate(row)
           if data["tutors"][t]["id"] != data["goals"][g]["match"]]

    # Độ trễ: mỗi lần một văn bản (như worker tính một nhu cầu hay một hồ sơ), sau khi làm nóng.
    texts = goals + tutors
    for text in texts[:WARMUP]:
        model.encode([text], normalize_embeddings=True)
    timings = []
    for _ in range(LATENCY_REPEATS):
        for text in texts:
            t0 = time.perf_counter()
            model.encode([text], normalize_embeddings=True)
            timings.append((time.perf_counter() - t0) * 1000)
    timings.sort()

    return {
        "model": candidate["id"],
        "revision": revision,
        "license": license_,
        "dimensions": int(np.asarray(g_emb).shape[1]),
        "snapshot_mb": round(size_mb, 1),
        "load_s": round(load_s, 2),
        "latency_ms": {
            "median": round(statistics.median(timings), 1),
            "p95": round(timings[int(0.95 * len(timings)) - 1], 1),
            "samples": len(timings),
        },
        "s_spread": {
            "pos_mean": round(statistics.fmean(pos), 4),
            "neg_mean": round(statistics.fmean(neg), 4),
            "min": round(min(min(row) for row in s), 4),
            "max": round(max(max(row) for row in s), 4),
        },
        **{k: v for k, v in metrics.items()},
    }


def environment() -> dict:
    import torch

    return {
        "python": sys.version.split()[0],
        "platform": platform.platform(),
        "processor": platform.processor(),
        "cpu_count": os.cpu_count(),
        "torch_threads": torch.get_num_threads(),
        "device": "cpu",
        "packages": {name: version(name) for name in
                     ["torch", "sentence-transformers", "transformers", "huggingface-hub", "numpy", "pyvi"]},
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()

    data = load_dataset()
    result = {
        "dataset": data["version"],
        "goals": len(data["goals"]),
        "tutors": len(data["tutors"]),
        "environment": environment(),
        "baseline_bag_of_words": lexical_baseline(data),
        "models": [evaluate_model(c, data) for c in CANDIDATES],
    }
    args.out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    for row in [("baseline", result["baseline_bag_of_words"])] + [(m["model"], m) for m in result["models"]]:
        name, m = row
        print(f"{name}: top1={m['top1']:.2f} top3={m['top3']:.2f} mrr={m['mrr']:.3f} auc={m['auc']:.3f}"
              + (f" p95={m['latency_ms']['p95']}ms dim={m['dimensions']}" if "latency_ms" in m else ""))


if __name__ == "__main__":
    main()
