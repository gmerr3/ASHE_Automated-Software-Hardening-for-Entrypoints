import requests
import os
def get_top_maven_jars(limit=100):
    base_url = "https://search.maven.org/solrsearch/select"
    jar_urls = []
    query_params = {
        "q": "*:*",             # Wildcard search to fetch all artifacts
        "rows": limit*2,          # Number of results
        "wt": "json",           # Response format
        "core": "gavl",
        "sort": "downloaded desc"  # Sort by popularity (downloads)
    }
    
    try:
        # Fetch the top Maven artifacts
        response = requests.get(base_url, params=query_params)
        response.raise_for_status()
        data = response.json()
        
        docs = data.get("response", {}).get("docs", [])
        for doc in docs:
            tags = doc.get("tags", [])
            group_id = doc.get("g")
            artifact_id = doc.get("a")
            latest_version = doc.get("latestVersion")
            if "scala" in tags or "scala" in artifact_id:
                continue  # Skip Scala-related artifacts
            print(tags)
            
            if group_id and artifact_id and latest_version:
                # Construct direct JAR URL
                jar_url = f"https://repo1.maven.org/maven2/{group_id.replace('.', '/')}/{artifact_id}/{latest_version}/{artifact_id}-{latest_version}.jar"
                jar_urls.append(jar_url)
            if len(jar_urls) > limit:
                break
        return jar_urls
    except Exception as e:
        print(f"Error fetching data: {e}")
        return []
def download_jars(jar_urls, output_dir="jars"):
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)  # Create output directory if it doesn't exist
    
    for url in jar_urls:
        try:
            print(f"Downloading {url}...")
            response = requests.get(url, stream=True)
            response.raise_for_status()
            
            # Extract filename from the URL
            filename = os.path.join(output_dir, url.split("/")[-1])
            
            # Save the JAR file locally
            with open(filename, "wb") as file:
                for chunk in response.iter_content(chunk_size=1024):
                    file.write(chunk)
            
            print(f"Saved to {filename}")
        except Exception as e:
            print(f"Failed to download {url}: {e}")
if __name__ == "__main__":
    top_jar_urls = get_top_maven_jars()
    for url in top_jar_urls:
        print(url)
    download_jars(top_jar_urls,"top100")
    open("top100.txt",'w').write('\n'.join(top_jar_urls))