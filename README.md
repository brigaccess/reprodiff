# diff tool for reproducible builds

The current minimalistic implementation only checks whether the provided files 
have matching size and SHA-256 hashes. 

For the proposed architecture, this tool should provide an adequate validation
of binary equality.

### Further possible improvements
- Speed up the SHA256 by leveraging [ACCP](https://github.com/corretto/amazon-corretto-crypto-provider) (limits the portability though)
- More supported introspection formats (elf symbols/sections introspection, image metadata maybe?)
