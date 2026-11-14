use serde::Serialize;

#[derive(Debug, Clone, Serialize)]
pub struct SecurityQuestionDefinition {
    pub key: &'static str,
    pub prompt: &'static str,
}

pub const STANDARD_SECURITY_QUESTIONS: &[SecurityQuestionDefinition] = &[
    SecurityQuestionDefinition {
        key: "first_pet",
        prompt: "What was the name of your first pet?",
    },
    SecurityQuestionDefinition {
        key: "first_car",
        prompt: "What was the make/model of your first car or vehicle?",
    },
    SecurityQuestionDefinition {
        key: "childhood_city",
        prompt: "In what city or town did you grow up?",
    },
    SecurityQuestionDefinition {
        key: "primary_school",
        prompt: "What was the name of your first primary or elementary school?",
    },
    SecurityQuestionDefinition {
        key: "mothers_birthplace",
        prompt: "What city was your mother born in?",
    },
    SecurityQuestionDefinition {
        key: "favorite_book",
        prompt: "What was the title of your favorite book growing up?",
    },
];

pub fn get_question_prompt(key: &str) -> Option<&'static str> {
    STANDARD_SECURITY_QUESTIONS
        .iter()
        .find(|q| q.key == key)
        .map(|q| q.prompt)
}

pub fn is_valid_question_key(key: &str) -> bool {
    STANDARD_SECURITY_QUESTIONS.iter().any(|q| q.key == key)
}

