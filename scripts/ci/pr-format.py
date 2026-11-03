"""Checks the pull request title and the author's own explanation (docs/process/git-workflow.md).

Reads PR_TITLE and PR_BODY from the environment, never from the command line, so that text written
in the pull request cannot run as shell.
"""
import os
import re
import sys

TITLE = re.compile(r"^(feat|fix|refactor|test|docs|chore|ci|build)\([a-z0-9-]+\): [^\[\]]+ \[TB-[0-9]+\]$")
AUTHOR_SECTION = "## Tác giả tự giải thích"
QUESTIONS = (
    "**Thay đổi này làm gì và tại sao:**",
    "**Tình huống xấu nhất đã xét:**",
    "**Phần tôi muốn người rà xem kỹ:**",
)
TEMPLATE_HINT = "Tự viết, không để agent viết hộ."


def author_answers(body):
    """Returns the text written under each question, or None when the section is missing."""
    body = re.sub(r"<!--.*?-->", "", body.replace("\r\n", "\n"), flags=re.S)
    start = body.find(AUTHOR_SECTION)
    if start < 0:
        return None
    section = body[start + len(AUTHOR_SECTION):]
    end = re.search(r"^## ", section, flags=re.M)
    section = section[: end.start()] if end else section

    positions = [section.find(question) for question in QUESTIONS]
    if min(positions) < 0:
        return None
    bounds = sorted(zip(positions, QUESTIONS)) + [(len(section), "")]
    answers = {}
    for (position, question), (following, _) in zip(bounds, bounds[1:]):
        answers[question] = section[position + len(question):following].replace(TEMPLATE_HINT, "").strip()
    return answers


def main():
    title = os.environ.get("PR_TITLE", "")
    body = os.environ.get("PR_BODY", "")
    problems = []

    if not TITLE.match(title):
        problems.append(f"title must look like 'feat(classes): accept offers [TB-7]', got: {title!r}")

    answers = author_answers(body)
    if answers is None:
        problems.append(f"body must keep the '{AUTHOR_SECTION}' section with its three questions")
    else:
        problems += [f"answer missing for {question}" for question, text in answers.items() if not text]

    for problem in problems:
        print(f"pr-format: {problem}", file=sys.stderr)
    if problems:
        return 1
    print("pr-format: ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
