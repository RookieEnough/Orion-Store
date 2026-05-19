import json
import requests
import re
import os

# --- DATA SOURCES ---
# We focus on "Recent" threats to keep the DB lean and relevant for modern apps.
THREATFOX_URLS = ["https://threatfox.abuse.ch/export/csv/recent/"]
MALWARE_BAZAAR_URLS = ["https://bazaar.abuse.ch/export/txt/sha256/recent/"]
AARYAN_BASE_URL = "https://raw.githubusercontent.com/aaryanrlondhe/Malware-Hash-Database/main/SHA256/sha256_hashes_{}.txt"

# Regex for SHA256 (64 hex chars)
HASH_PATTERN = re.compile(r'\b[a-fA-F0-9]{64}\b')

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/plain,text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
}

def get_hashes(text):
    return set(HASH_PATTERN.findall(text))

def fetch_simple_source(name, urls):
    print(f"   🔎 Fetching {name}...")
    hashes = set()
    for url in urls:
        try:
            r = requests.get(url, headers=HEADERS, timeout=45)
            if r.status_code == 200:
                found = get_hashes(r.text)
                hashes.update(found)
                print(f"      ✅ {name}: {len(found)} signatures.")
            else:
                print(f"      ⚠️ {name} Error ({r.status_code})")
        except Exception as e:
            print(f"      ❌ {name} Exception: {str(e)[:50]}")
    return hashes

def fetch_archive_source():
    print(f"   🔎 Fetching Malware Hash Archive (1-6)...")
    all_hashes = set()
    # We pull a few parts to get a good baseline without 1GB download
    for i in range(1, 4): 
        url = AARYAN_BASE_URL.format(i)
        try:
            print(f"      ...Downloading Part {i}")
            r = requests.get(url, headers=HEADERS, timeout=60)
            if r.status_code == 200:
                count = 0
                for line in r.iter_lines(decode_unicode=True):
                    if line:
                        clean = line.strip().lower()
                        if len(clean) == 64:
                            all_hashes.add(clean)
                            count += 1
                print(f"      ✅ Part {i}: {count} signatures.")
            else:
                print(f"      ⚠️ Part {i} Missing ({r.status_code})")
        except Exception as e:
            print(f"      ❌ Part {i} Failed: {str(e)[:50]}")
    return all_hashes

def run():
    print("🛡️ Orion Sentinel Compiler (v1.0)")
    
    # 1. Fetch Data
    tf_hashes = fetch_simple_source("ThreatFox", THREATFOX_URLS)
    mb_hashes = fetch_simple_source("MalwareBazaar", MALWARE_BAZAAR_URLS)
    archive_hashes = fetch_archive_source()

    # 2. Compile into Buckets (0-9, a-f)
    print("\n   ⚙️  Sharding Database into 16 buckets...")
    
    # Initialize 16 buckets
    buckets = {hex(i)[2:]: [] for i in range(16)}
    
    processed_hashes = set()

    # Helper to add
    def add_to_bucket(hash_set, label):
        for h in hash_set:
            if h not in processed_hashes:
                # Determine bucket char (first char of hash)
                bucket_char = h[0]
                
                # We save minimal data to save bandwidth
                entry = {"h": h} 
                if "Archive" not in label:
                    entry["n"] = label # Tag specific source only if recent/critical
                
                buckets[bucket_char].append(entry)
                processed_hashes.add(h)

    # Priority Order: Newest First
    add_to_bucket(tf_hashes, "ThreatFox")
    add_to_bucket(mb_hashes, "MalwareBazaar")
    add_to_bucket(archive_hashes, "Archive")

    # Manual Test Keys (EICAR, etc)
    manual = [
        ("275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f", "EICAR-Test"),
        ("5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8", "Orion-Test"),
    ]
    for h, n in manual:
        if h not in processed_hashes:
            bucket_char = h[0]
            buckets[bucket_char].append({"h": h, "n": n})
            processed_hashes.add(h)

    # 3. Write Shards
    total_count = 0
    if not os.path.exists("sentinel"):
        os.makedirs("sentinel")

    print("\n   💾 Saving Shards...")
    for char, data in buckets.items():
        # Sort for better GZIP compression downstream
        data.sort(key=lambda x: x['h'])
        
        filename = f"sentinel/shard_{char}.json"
        with open(filename, "w") as f:
            # Separators remove whitespace for smaller size
            json.dump(data, f, separators=(',', ':'))
        
        print(f"      📦 {filename}: {len(data)} entries")
        total_count += len(data)

    print(f"\n📦 Total Unique Signatures: {total_count}")

if __name__ == "__main__":
    run()