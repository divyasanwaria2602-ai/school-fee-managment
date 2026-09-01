-- Seed the standard school classes for every existing school.
-- New schools are also provisioned with the same classes in UserManagementService.
INSERT INTO classes (school_id, name, section, active)
SELECT s.id, v.name, '', TRUE
FROM schools s
CROSS JOIN (VALUES
  ('Nursery'), ('LKG'), ('UKG'),
  ('1'), ('2'), ('3'), ('4'), ('5'), ('6'), ('7'),
  ('8'), ('9'), ('10'), ('11'), ('12')
) AS v(name)
WHERE NOT EXISTS (
  SELECT 1
  FROM classes c
  WHERE c.school_id = s.id
    AND c.name = v.name
    AND c.section = ''
);
