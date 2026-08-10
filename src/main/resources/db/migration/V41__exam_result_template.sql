-- Expose the exam-result WhatsApp message as an editable system template (it was
-- previously built inline in code). Body only (WhatsApp-style, null title).
INSERT INTO message_templates (code, name, channel, title, body, variables, is_system)
VALUES (
    'exam_result',
    'نتيجة الاختبار لولي الأمر',
    'whatsapp',
    NULL,
    'حصل {student.name} على {exam.score} من {exam.max}{exam.bonus} في اختبار "{exam.name}".',
    'student.name,exam.score,exam.max,exam.bonus,exam.name',
    true
)
ON CONFLICT (code) DO NOTHING;
