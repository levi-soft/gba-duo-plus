#ifndef PLUS_STUDIO_MEMORY_RANGE_H
#define PLUS_STUDIO_MEMORY_RANGE_H
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

static inline bool plus_studio_range_valid(uint64_t address, uint64_t length, size_t rom_size) {
    if (length < 1 || length > 65536) return false;
    const uint64_t regions[][2] = {
        {0x02000000, 0x02040000}, {0x03000000, 0x03008000},
        {0x05000000, 0x05000400}, {0x06000000, 0x06018000},
        {0x07000000, 0x07000400}
    };
    for (size_t i = 0; i < sizeof(regions) / sizeof(regions[0]); ++i)
        if (address >= regions[i][0] && address < regions[i][1] && length <= regions[i][1] - address) return true;
    if (rom_size > 0 && rom_size <= 0x02000000 && address >= 0x08000000 && address < 0x08000000 + rom_size)
        return length <= 0x08000000 + rom_size - address;
    return false;
}
#endif
