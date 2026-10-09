import re

with open('backend-java/src/main/java/co/palaciodelmar/backend/config/DataInitializer.java', 'r') as f:
    content = f.read()

def replace_images(match):
    urls = re.findall(r'"([^"]+)"', match.group(1))
    urls = [u for u in urls if 'unsplash.com' in u]
    if len(urls) == 0:
        return match.group(0)
    
    # Pad to 4 images
    while len(urls) < 4:
        # Just duplicate the last one or add a random one
        urls.append(urls[-1])
    
    # Take at most 4
    urls = urls[:4]
    
    new_list = ',\n                            '.join([f'"{u}"' for u in urls])
    return f'.images(List.of(\n                            {new_list}\n                    ))'

new_content = re.sub(r'\.images\(List\.of\((.*?)\)\)', replace_images, content, flags=re.DOTALL)

with open('backend-java/src/main/java/co/palaciodelmar/backend/config/DataInitializer.java', 'w') as f:
    f.write(new_content)
