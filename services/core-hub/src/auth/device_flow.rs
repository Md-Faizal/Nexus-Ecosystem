use rand::Rng;

const USER_CODE_CHARS: &[u8] = b"BCDFGHJKLMNPQRSTVWXYZ23456789";

/// Generate a user-friendly code for display in terminal (e.g. "WDJB-MJHT")
pub fn generate_user_code() -> String {
    let mut rng = rand::thread_rng();
    let mut part1 = String::with_capacity(4);
    let mut part2 = String::with_capacity(4);

    for _ in 0..4 {
        let idx = rng.gen_range(0..USER_CODE_CHARS.len());
        part1.push(USER_CODE_CHARS[idx] as char);
    }
    for _ in 0..4 {
        let idx = rng.gen_range(0..USER_CODE_CHARS.len());
        part2.push(USER_CODE_CHARS[idx] as char);
    }

    format!("{}-{}", part1, part2)
}

/// Normalize user input code by stripping spaces, hyphens and uppercasing
pub fn normalize_user_code(input: &str) -> String {
    let cleaned: String = input
        .chars()
        .filter(|c| c.is_ascii_alphanumeric())
        .collect::<String>()
        .to_uppercase();

    if cleaned.len() == 8 {
        format!("{}-{}", &cleaned[0..4], &cleaned[4..8])
    } else {
        cleaned
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_user_code_generation() {
        let code = generate_user_code();
        assert_eq!(code.len(), 9);
        assert_eq!(&code[4..5], "-");
    }

    #[test]
    fn test_normalize_code() {
        assert_eq!(normalize_user_code("wdjb-mjht"), "WDJB-MJHT");
        assert_eq!(normalize_user_code("wdjb mjht"), "WDJB-MJHT");
        assert_eq!(normalize_user_code("WDJBMJHT"), "WDJB-MJHT");
    }
}

