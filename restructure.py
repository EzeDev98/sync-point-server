#!/usr/bin/env python3
# ---------------------------------------------------------------------------
# SyncPoint server  ·  Phase 1: package-by-layer  ->  package-by-feature
# ---------------------------------------------------------------------------
# Moves every .java file into its feature/shared package, rewrites the
# `package` declaration and all imports (including static imports), inserts
# imports that become necessary once former same-package siblings are split
# apart, prunes the emptied old folders, then self-verifies that every
# referenced project class still resolves.
#
# Root package stays com.sp.syncPoint, so component scan / @EntityScan /
# Flyway need NO config change. Filenames are unchanged (move only).
#
# USAGE:  run from the project root (the folder that contains `src` and `pom.xml`)
#     python3 restructure.py
# or point it at the project:
#     python3 restructure.py /path/to/sync-point-server
#
# RECOMMENDED: commit or back up first, so `git diff` shows you the change.
# ---------------------------------------------------------------------------
import os, re, sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
BASE = "com.sp.syncPoint"

# class simple name -> target package, relative to com.sp.syncPoint ("" = root).
# Anything not listed that currently lives in common.exception stays put
# (the global handler references them all); tests follow the class they test.
MAP = {
    "SyncPointApplication": "",
    "AppConfig": "config", "RedissonConfig": "config", "SwaggerCustomConfig": "config",
    "MoneyUtils": "common.money", "MoneyConfig": "common.money",
    "BaseResponse": "common.response", "ResponseService": "common.response",
    "ResponseServiceImpl": "common.response",
    "WebSecurityConfig": "common.security", "KeycloakConfig": "common.security",
    "KeycloakJwtRolesConverter": "common.security", "RolesEvaluator": "common.security",
    "CustomAuthenticationEntryPoint": "common.security", "CustomAccessDenied": "common.security",
    "OtpResetEngine": "common.reset", "ResetValidator": "common.reset", "OtpService": "common.reset",
    "ResettableOtp": "common.reset", "ResettableToken": "common.reset",
    "OtpDeliveryService": "common.reset.delivery",
    "ContiguityOtpDeliveryService": "common.reset.delivery",
    "LoggingOtpDeliveryService": "common.reset.delivery",
    "VerifyOtpRequest": "common.reset.dto", "VerifyOtpResponse": "common.reset.dto",
    "EntityUtilityService": "common.support", "EntityUtilityServiceImpl": "common.support",
    "RateLimiterService": "common.util", "UniqueValueGeneratorService": "common.util",
    "GeneralConstants": "common.constants",
    # auth
    "AuthController": "feature.auth.web",
    "AuthService": "feature.auth.service",
    "AuthServiceImpl": "feature.auth.service.impl", "KeycloakService": "feature.auth.service.impl",
    "RegistrationValidator": "feature.auth.service.impl",
    "RegistrationRequest": "feature.auth.dto", "UserRequest": "feature.auth.dto",
    "LoginResponse": "feature.auth.dto", "UserRegistrationResponse": "feature.auth.dto",
    # user
    "UserController": "feature.user.web", "UserService": "feature.user.service",
    "UserServiceImpl": "feature.user.service.impl", "CurrentUserService": "feature.user.service.impl",
    "User": "feature.user.model", "UserRepository": "feature.user.repository",
    "GenderStatus": "feature.user.enums", "UserStatus": "feature.user.enums",
    # session
    "UserSessionController": "feature.session.web", "LogoutControl": "feature.session.web",
    "SessionManagementService": "feature.session.service", "LogoutService": "feature.session.service",
    "SessionManagementServiceImpl": "feature.session.service.impl",
    "LogoutServiceImpl": "feature.session.service.impl",
    "UserSession": "feature.session.model", "UserSessionRepository": "feature.session.repository",
    "LogoutResponse": "feature.session.dto", "InvalidatedSessionResponse": "feature.session.dto",
    # wallet
    "WalletController": "feature.wallet.web",
    "WalletProvisioningService": "feature.wallet.service", "WalletAuthService": "feature.wallet.service",
    "WalletPinService": "feature.wallet.service", "WalletFundingService": "feature.wallet.service",
    "WalletProvisioningServiceImpl": "feature.wallet.service.impl",
    "WalletAuthServiceImpl": "feature.wallet.service.impl",
    "WalletPinServiceImpl": "feature.wallet.service.impl",
    "WalletFundingServiceImpl": "feature.wallet.service.impl",
    "WalletLockoutService": "feature.wallet.service.impl",
    "SystemWalletInitializer": "feature.wallet.service.impl",
    "PinValidator": "feature.wallet.service.impl",
    "Wallet": "feature.wallet.model", "SystemWallet": "feature.wallet.model",
    "WalletFundingRecord": "feature.wallet.model",
    "WalletRepository": "feature.wallet.repository",
    "SystemWalletRepository": "feature.wallet.repository",
    "WalletFundingRecordRepository": "feature.wallet.repository",
    "SetPinRequest": "feature.wallet.dto", "PinRequest": "feature.wallet.dto",
    "VerifyPinRequest": "feature.wallet.dto", "WalletTransferRequest": "feature.wallet.dto",
    "WalletFundingResponse": "feature.wallet.dto",
    # transaction
    "TransactionController": "feature.transaction.web",
    "TransactionQueryService": "feature.transaction.service",
    "TransactionSyncService": "feature.transaction.service",
    "TransactionQueryServiceImpl": "feature.transaction.service.impl",
    "TransactionSyncServiceImpl": "feature.transaction.service.impl",
    "TransactionLockService": "feature.transaction.service.impl",
    "TransactionValidator": "feature.transaction.service.impl",
    "IdempotencyValidator": "feature.transaction.service.impl",
    "DtoMapper": "feature.transaction.mapper",
    "Transaction": "feature.transaction.model",
    "TransactionRepository": "feature.transaction.repository",
    "TransactionUtils": "feature.transaction.util",
    "SyncTransactionRequest": "feature.transaction.dto", "ProcessingResult": "feature.transaction.dto",
    "TransactionResponse": "feature.transaction.dto",
    "EntryType": "feature.transaction.enums", "Outcome": "feature.transaction.enums",
    "TransactionStatus": "feature.transaction.enums", "TransactionType": "feature.transaction.enums",
    # passwordreset
    "PasswordResetController": "feature.passwordreset.web",
    "PasswordResetService": "feature.passwordreset.service",
    "PasswordResetServiceImpl": "feature.passwordreset.service.impl",
    "PasswordResetOtp": "feature.passwordreset.model", "PasswordResetToken": "feature.passwordreset.model",
    "PasswordResetOtpRepository": "feature.passwordreset.repository",
    "PasswordResetTokenRepository": "feature.passwordreset.repository",
    "PasswordResetProperties": "feature.passwordreset.config",
    "PasswordOtp": "feature.passwordreset.constants", "PasswordToken": "feature.passwordreset.constants",
    "PasswordResetStatus": "feature.passwordreset.enums",
    "PasswordResetRequestDto": "feature.passwordreset.dto",
    "CompletePasswordResetRequest": "feature.passwordreset.dto",
    "PasswordResetResponse": "feature.passwordreset.dto",
    # pinreset
    "PinResetController": "feature.pinreset.web",
    "PinResetService": "feature.pinreset.service",
    "PinResetServiceImpl": "feature.pinreset.service.impl",
    "PinResetOtp": "feature.pinreset.model", "PinResetToken": "feature.pinreset.model",
    "PinResetOtpRepository": "feature.pinreset.repository",
    "PinResetTokenRepository": "feature.pinreset.repository",
    "PinResetProperties": "feature.pinreset.config",
    "PinOtp": "feature.pinreset.constants", "PinToken": "feature.pinreset.constants",
    "PinResetRequestDto": "feature.pinreset.dto", "CompletePinResetRequest": "feature.pinreset.dto",
    "VerifyPinOtpRequest": "feature.pinreset.dto", "PinResetResponse": "feature.pinreset.dto",
}

def relpkg(p):
    if p == BASE: return ""
    return p[len(BASE)+1:] if p and p.startswith(BASE + ".") else None
def scan(root):
    out = []
    for dp, _, ns in os.walk(root):
        for n in ns:
            if n.endswith(".java"): out.append(os.path.join(dp, n))
    return out
def read(p):
    with open(p, encoding="utf-8") as f: return f.read()
def write(p, s):
    with open(p, "w", encoding="utf-8") as f: f.write(s)
def get_pkg(t):
    m = re.search(r'^\s*package\s+([\w.]+)\s*;', t, re.M); return m.group(1) if m else None
def strip_cs(t):
    t = re.sub(r'/\*.*?\*/', ' ', t, flags=re.S); t = re.sub(r'//[^\n]*', ' ', t)
    return re.sub(r'"(\\.|[^"\\])*"', ' ', t)

def main():
    main_root = os.path.join(ROOT, "src/main/java", *BASE.split("."))
    if not os.path.isdir(main_root):
        sys.exit(f"ERROR: {main_root} not found. Run this from the project root "
                 f"(the folder containing `src` and `pom.xml`), or pass the path as an argument.")

    files = scan(os.path.join(ROOT, "src/main/java")) + scan(os.path.join(ROOT, "src/test/java"))
    index = {}
    for p in files:
        name = os.path.basename(p)[:-5]; t = read(p)
        index[name] = {"pkg": get_pkg(t), "path": p,
                       "test": "/src/test/" in p.replace("\\", "/")}

    # desired package for every class
    desired = {}
    for name, info in index.items():
        if info["test"]:
            subj = name[:-5] if name.endswith("Tests") else (name[:-4] if name.endswith("Test") else name)
            if subj in MAP: desired[name] = MAP[subj]
            elif subj in index: desired[name] = relpkg(index[subj]["pkg"])
            else: desired[name] = relpkg(info["pkg"])
        elif name in MAP: desired[name] = MAP[name]
        elif info["pkg"] == BASE + ".common.exception": desired[name] = "common.exception"
        else:
            sys.exit(f"ERROR: unmapped class {name} ({info['pkg']}). "
                     f"Aborting so nothing is misplaced.")

    final_pkg = {n: BASE + ("." + desired[n] if desired[n] else "") for n in index}
    final_fqn = {n: final_pkg[n] + "." + n for n in index}

    # 1) move + fix own package line
    moved = 0
    for name, info in index.items():
        np = final_pkg[name]
        if np == info["pkg"]: continue
        base_src = "src/test/java" if info["test"] else "src/main/java"
        nd = os.path.join(ROOT, base_src, *np.split(".")); os.makedirs(nd, exist_ok=True)
        t = re.sub(r'(^\s*package\s+)[\w.]+(\s*;)', r'\g<1>' + np + r'\g<2>', read(info["path"]),
                   count=1, flags=re.M)
        write(info["path"], t)
        newp = os.path.join(nd, name + ".java"); os.replace(info["path"], newp); info["path"] = newp
        moved += 1

    # 2) rewrite FQNs (imports, static imports, inline) for every moved class
    changed = [(index[n]["pkg"] + "." + n, final_fqn[n]) for n in index
               if final_pkg[n] != index[n]["pkg"]]
    for name, info in index.items():
        t = read(info["path"]); o = t
        for old, new in changed:
            t = re.sub(r'(?<![\w.])' + re.escape(old) + r'(?![\w])', new, t)
        if t != o: write(info["path"], t)

    # 3) insert imports that are now required across the new package boundaries
    inserted = 0
    for name, info in index.items():
        t = read(info["path"]); pkg = final_pkg[name]
        exp = set(re.findall(r'^\s*import\s+([\w.]+)\s*;', t, re.M))
        stat = set(re.findall(r'^\s*import\s+static\s+([\w.]+)\.\w+\s*;', t, re.M))
        wild = set(re.findall(r'^\s*import\s+([\w.]+)\.\*\s*;', t, re.M))
        have = exp | stat
        body = "\n".join(l for l in strip_cs(t).split("\n")
                         if not re.match(r'\s*(package|import)\s', l))
        refs = set(re.findall(r'\b([A-Z][A-Za-z0-9_]*)\b', body))
        need = []
        for other in index:
            if other == name or other not in refs: continue
            ofqn, opkg = final_fqn[other], final_pkg[other]
            if opkg == pkg or ofqn in have or opkg in wild: continue
            need.append(ofqn)
        if need:
            lines = t.split("\n")
            pi = next((i for i, l in enumerate(lines) if re.match(r'\s*package\s', l)), -1)
            lines[pi+1:pi+1] = [""] + ["import " + fq + ";" for fq in sorted(need)]
            write(info["path"], "\n".join(lines)); inserted += len(need)

    # 4) prune emptied directories
    pruned = 0
    for base_src in ("src/main/java", "src/test/java"):
        rootp = os.path.join(ROOT, base_src)
        for dp, _, _ in sorted(os.walk(rootp), reverse=True):
            if dp == rootp: continue
            if not os.listdir(dp): os.rmdir(dp); pruned += 1

    # 5) self-verify: every referenced project class resolves
    files2 = scan(os.path.join(ROOT, "src/main/java")) + scan(os.path.join(ROOT, "src/test/java"))
    name2fqn, fqns, meta = {}, set(), {}
    for p in files2:
        t = read(p); nm = os.path.basename(p)[:-5]; pk = get_pkg(t) or ""
        fq = (pk + "." + nm) if pk else nm
        name2fqn[nm] = fq; fqns.add(fq); meta[p] = (nm, pk, t)
    problems = []
    for p, (nm, pk, t) in meta.items():
        exp = set(re.findall(r'^\s*import\s+([\w.]+)\s*;', t, re.M))
        stat = set(re.findall(r'^\s*import\s+static\s+([\w.]+)\.\w+\s*;', t, re.M))
        wild = set(re.findall(r'^\s*import\s+([\w.]+)\.\*\s*;', t, re.M))
        for imp in exp | stat:
            if imp.startswith(BASE) and imp not in fqns:
                problems.append(f"STALE import {imp}  in {p}")
        body = "\n".join(l for l in strip_cs(t).split("\n")
                         if not re.match(r'\s*(package|import)\s', l))
        for r in set(re.findall(r'\b([A-Z][A-Za-z0-9_]*)\b', body)):
            fq = name2fqn.get(r)
            if not fq or r == nm: continue
            rpk = fq.rsplit(".", 1)[0]
            if rpk == pk or fq in exp or fq in stat or rpk in wild: continue
            problems.append(f"UNRESOLVED {r} (needs {fq})  in {p}")

    print(f"\nmoved={moved}  inserted_imports={inserted}  pruned_dirs={pruned}")
    if problems:
        print("\n!! SELF-CHECK FOUND ISSUES (open these in your IDE and let auto-import fix):")
        for x in sorted(set(problems)): print("   " + x)
        sys.exit(1)
    print("SELF-CHECK OK — every referenced class resolves; no stale imports.")
    print("\nNext: `git add -A && git diff --cached --stat`, then in IntelliJ run "
          "Optimize Imports (Ctrl+Alt+O) on the project and Build (Ctrl+F9). "
          "Run the test suite to confirm nothing regressed.")

if __name__ == "__main__":
    main()
