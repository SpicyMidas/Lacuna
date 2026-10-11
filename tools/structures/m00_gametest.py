"""GameTest templates: a 9x6x9 platform (stone floor at y=0, explicit air above)."""


def build(Template):
    t = Template("gametest_platform", 9, 6, 9)
    t.fill(0, 0, 0, 8, 0, 8, "stone")
    t.fill(0, 1, 0, 8, 5, 8, "air")
    t.save()
